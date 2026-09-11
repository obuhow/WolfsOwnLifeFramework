/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.importxlsx.dto.ImportApplyResponse;
import ru.wolf.api.importxlsx.dto.ImportPreviewResponse;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 1.4 ticket 02 — the happy path the ticket is named after: upload the schedule, see a
 * preview, press «Применить», and find your real Записи времени in WOLF.
 *
 * <p>Drives the real HTTP API against real PostgreSQL. The workbook is built in-process in the
 * owner's grid format (date serials in row 1, time fractions in column C, activities in the day
 * columns) because the real {@code Расписание.xlsx} is personal data and stays out of the repo.
 */
class XlsxSchedulePreviewApiIT extends ApiIntegrationTest {

    private static final int SERIAL_2026_06_01 = 46174;
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    TimeEntryRepository timeEntryRepository;

    @Autowired
    XlsxImportRunRepository runRepository;

    @Autowired
    XlsxImportQuestionRepository questionRepository;

    @Autowired
    ActivityMappingRepository mappingRepository;

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanup() {
        timeEntryRepository.deleteAll();
        questionRepository.deleteAll();
        runRepository.deleteAll();
        mappingRepository.deleteAll();
        deloRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
    }

    /**
     * Two sheets, four filled cells: «Java» three times (once on the second week) and «Спортзал»
     * once. Chosen so the preview has both a known and an unknown activity to report.
     */
    private byte[] twoWeekSchedule() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet first = wb.createSheet("1-7 июня");
            Row dates1 = first.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates1.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row morning = first.createRow(2);
            morning.createCell(2).setCellValue(timeFraction(7, 0));
            morning.createCell(3).setCellValue("Java");       // Mon 1 June 07:00
            morning.createCell(4).setCellValue("Спортзал");   // Tue 2 June 07:00
            Row later = first.createRow(3);
            later.createCell(2).setCellValue(timeFraction(8, 0));
            later.createCell(5).setCellValue("Java");         // Wed 3 June 08:00

            Sheet second = wb.createSheet("8-14 июня");
            Row dates2 = second.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates2.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + 7 + day);
            }
            Row nextWeek = second.createRow(2);
            nextWeek.createCell(2).setCellValue(timeFraction(7, 0));
            nextWeek.createCell(3).setCellValue("Java");      // Mon 8 June 07:00

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static double timeFraction(int hour, int minute) {
        return (hour * 60 + minute) / (24.0 * 60.0);
    }

    private static BodyInserters.MultipartInserter multipart(byte[] bytes) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "Расписание.xlsx";
            }
        }).contentType(MediaType.parseMediaType(XLSX_MIME));
        return BodyInserters.fromMultipartData(builder.build());
    }

    private ImportPreviewResponse preview(WebTestClient client, byte[] bytes) {
        return client.post()
                .uri("/api/v1/import/xlsx")
                .body(multipart(bytes))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportPreviewResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ImportApplyResponse apply(WebTestClient client, byte[] bytes) {
        return client.post()
                .uri("/api/v1/import/xlsx/apply")
                .body(multipart(bytes))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportApplyResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    @Test
    void preview_counts_what_would_be_created_and_writes_nothing() throws Exception {
        WebTestClient authed = authedAdminClient();
        byte[] bytes = twoWeekSchedule();

        ImportPreviewResponse preview = preview(authed, bytes);

        assertThat(preview).isNotNull();
        assertThat(preview.weeks()).isEqualTo(2);
        assertThat(preview.totalCells()).isEqualTo(4);
        assertThat(preview.timeEntriesToCreate()).isEqualTo(4);
        assertThat(preview.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(preview.to()).isEqualTo(LocalDate.of(2026, 6, 8));
        // Nothing is mapped yet, so both activities are new.
        assertThat(preview.knownActivities()).isZero();
        assertThat(preview.newActivities()).isEqualTo(2);
        assertThat(preview.alreadyImported()).isFalse();
        assertThat(preview.activities()).extracting(ImportPreviewResponse.ActivityPreview::activityText)
                .containsExactlyInAnyOrder("Java", "Спортзал");
        assertThat(preview.activities()).filteredOn(a -> a.activityText().equals("Java"))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.cells()).isEqualTo(3);
                    assertThat(a.known()).isFalse();
                    assertThat(a.deloTitle()).isNull();
                });

        // The whole point of the preview: looking must not write.
        assertThat(timeEntryRepository.count()).isZero();
        assertThat(runRepository.count()).isZero();
        assertThat(questionRepository.count()).isZero();
    }

    @Test
    void apply_creates_one_done_entry_per_cell_and_links_known_activities() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        byte[] bytes = twoWeekSchedule();
        ImportPreviewResponse preview = preview(authed, bytes);
        // Now one activity is known to the dictionary and one is not.
        assertThat(preview.knownActivities()).isEqualTo(1);
        assertThat(preview.newActivities()).isEqualTo(1);

        ImportApplyResponse applied = apply(authed, bytes);

        assertThat(applied).isNotNull();
        assertThat(applied.alreadyImported()).isFalse();
        assertThat(applied.created()).isEqualTo(4);
        assertThat(applied.skippedOccupied()).isZero();
        assertThat(applied.pendingQuestions()).isEqualTo(1);
        assertThat(applied.importRunId()).isNotNull();

        assertThat(timeEntryRepository.count()).isEqualTo(4);

        // One cell = one 15-minute Запись времени, and time is the user's LOCAL time: 07:00 in the
        // file is 07:00 in startAt, with no UTC conversion anywhere.
        var mondayJava = timeEntryRepository.findByUserIdAndStartAt(
                admin.getId(), LocalDateTime.of(2026, 6, 1, 7, 0)).orElseThrow();
        assertThat(mondayJava.getEndAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 7, 15));
        assertThat(mondayJava.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(mondayJava.getDelo().getTitle()).isEqualTo("Программирование");

        // The unknown activity still produces a Запись and raises a question, as before.
        var gym = timeEntryRepository.findByUserIdAndStartAt(
                admin.getId(), LocalDateTime.of(2026, 6, 2, 7, 0)).orElseThrow();
        assertThat(gym.getDelo()).isNull();
        assertThat(gym.getStatus()).isEqualTo(TimeEntry.Status.UNKNOWN);
        assertThat(questionRepository.count()).isEqualTo(1);
        assertThat(questionRepository.findAll().get(0).getActivityText()).isEqualTo("Спортзал");
    }

    @Test
    void preview_reports_mapped_delo_titles_for_known_activities() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        ImportPreviewResponse preview = preview(authed, twoWeekSchedule());

        assertThat(preview.activities()).filteredOn(a -> a.activityText().equals("Java"))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.known()).isTrue();
                    assertThat(a.deloTitle()).isEqualTo("Программирование");
                });
    }

    @Test
    void applying_the_same_file_twice_does_not_duplicate_anything() throws Exception {
        WebTestClient authed = authedAdminClient();
        byte[] bytes = twoWeekSchedule();

        ImportApplyResponse first = apply(authed, bytes);
        assertThat(first.created()).isEqualTo(4);
        long afterFirst = timeEntryRepository.count();

        ImportApplyResponse second = apply(authed, bytes);

        // Dedup by file hash is kept from the original import.
        assertThat(second.alreadyImported()).isTrue();
        assertThat(second.created()).isZero();
        assertThat(timeEntryRepository.count()).isEqualTo(afterFirst);
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void preview_flags_a_file_that_was_already_imported() throws Exception {
        WebTestClient authed = authedAdminClient();
        byte[] bytes = twoWeekSchedule();

        assertThat(preview(authed, bytes).alreadyImported()).isFalse();
        apply(authed, bytes);

        assertThat(preview(authed, bytes).alreadyImported()).isTrue();
    }

    @Test
    void apply_leaves_slots_that_are_already_occupied_untouched() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        // Someone already has a fact in the Monday 07:00 slot of the first week.
        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        LocalDateTime occupied = LocalDateTime.of(2026, 6, 1, 7, 0);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(existing).startAt(occupied).endAt(occupied.plusMinutes(15))
                .status(TimeEntry.Status.DONE).build());

        // SKIP_ALL is the default strategy, matching the pre-03 safe behaviour.
        ImportApplyResponse applied = apply(authed, twoWeekSchedule());

        assertThat(applied.skippedOccupied()).isEqualTo(1);
        assertThat(applied.overwritten()).isZero();
        assertThat(applied.created()).isEqualTo(3);
        // The pre-existing fact is not overwritten — conflict strategies are ticket 03's scope, and
        // the default keeps existing facts untouched.
        var kept = timeEntryRepository.findByUserIdAndStartAt(admin.getId(), occupied).orElseThrow();
        assertThat(kept.getDelo().getTitle()).isEqualTo("Уже было");
        assertThat(timeEntryRepository.count()).isEqualTo(4);
    }

    @Test
    void preview_counts_conflicting_cells_without_writing() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        // Seed two facts that the file would also produce (Mon 07:00 and Tue 07:00).
        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        for (LocalDateTime start : List.of(
                LocalDateTime.of(2026, 6, 1, 7, 0),
                LocalDateTime.of(2026, 6, 2, 7, 0))) {
            timeEntryRepository.save(TimeEntry.builder()
                    .user(admin).delo(existing).startAt(start).endAt(start.plusMinutes(15))
                    .status(TimeEntry.Status.DONE).build());
        }

        ImportPreviewResponse preview = preview(authed, twoWeekSchedule());

        // The preview must surface the conflict count before the user presses «Применить».
        assertThat(preview.conflictingCells()).isEqualTo(2);
        // Looking must still not write.
        assertThat(timeEntryRepository.count()).isEqualTo(2);
    }

    @Test
    void overwrite_all_replaces_existing_entries_with_file_data() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        // A pre-existing fact in the Monday 07:00 slot, linked to the wrong Дело.
        Delo oldDelo = deloRepository.save(Delo.builder().user(admin).title("Устаревшее").build());
        LocalDateTime occupied = LocalDateTime.of(2026, 6, 1, 7, 0);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(oldDelo).startAt(occupied).endAt(occupied.plusMinutes(15))
                .status(TimeEntry.Status.DONE).build());

        // «Java» is mapped, so the overwrite should re-link the slot to the correct Дело.
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        ImportApplyResponse applied = applyWithStrategy(authed, twoWeekSchedule(), "OVERWRITE_ALL");

        assertThat(applied.overwritten()).isEqualTo(1);
        assertThat(applied.skippedOccupied()).isZero();
        assertThat(applied.created()).isEqualTo(3);
        assertThat(timeEntryRepository.count()).isEqualTo(4); // replaced in place, no duplicate

        // The conflicting slot now carries the file's activity and Дело.
        var replaced = timeEntryRepository.findByUserIdAndStartAt(admin.getId(), occupied).orElseThrow();
        assertThat(replaced.getDelo().getTitle()).isEqualTo("Программирование");
        assertThat(replaced.getStatus()).isEqualTo(TimeEntry.Status.DONE);
    }

    @Test
    void cancel_writes_nothing_and_leaves_existing_facts_intact() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        LocalDateTime occupied = LocalDateTime.of(2026, 6, 1, 7, 0);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(existing).startAt(occupied).endAt(occupied.plusMinutes(15))
                .status(TimeEntry.Status.DONE).build());
        long before = timeEntryRepository.count();

        ImportApplyResponse applied = applyWithStrategy(authed, twoWeekSchedule(), "CANCEL");

        assertThat(applied.cancelled()).isTrue();
        assertThat(applied.created()).isZero();
        assertThat(applied.skippedOccupied()).isZero();
        assertThat(applied.overwritten()).isZero();
        assertThat(applied.importRunId()).isNull();
        assertThat(timeEntryRepository.count()).isEqualTo(before);
        assertThat(runRepository.count()).isZero();
    }

    @Test
    void questions_for_unknown_activities_are_exposed_for_the_resolve_flow() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        // «Java» is known, so only «Спортзал» remains unknown and raises a question.
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        ImportApplyResponse applied = apply(authed, twoWeekSchedule());

        var questions = authed.get()
                .uri("/api/v1/import/xlsx/{id}/questions", applied.importRunId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(XlsxImportQuestionService.XlsxImportQuestionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).activityText()).isEqualTo("Спортзал");
        assertThat(questions.get(0).sheetName()).isEqualTo("1-7 июня");
        assertThat(questions.get(0).startAt()).isEqualTo(LocalDateTime.of(2026, 6, 2, 7, 0));
    }

    @Test
    void unauthenticated_preview_is_rejected() throws Exception {
        webTestClient.post()
                .uri("/api/v1/import/xlsx")
                .body(multipart(twoWeekSchedule()))
                .exchange()
                .expectStatus().isForbidden();
    }

    /** Apply variant that passes an explicit conflict strategy as a form field. */
    private ImportApplyResponse applyWithStrategy(WebTestClient client, byte[] bytes, String strategy) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "Расписание.xlsx";
            }
        }).contentType(MediaType.parseMediaType(XLSX_MIME));
        builder.part("conflictStrategy", strategy);
        return client.post()
                .uri("/api/v1/import/xlsx/apply")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportApplyResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
