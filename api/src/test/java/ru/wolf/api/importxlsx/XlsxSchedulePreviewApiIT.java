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
import java.util.Map;

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
    void apply_creates_entries_and_links_known_activities() throws Exception {
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

        // Non-contiguous cells remain separate entries, and time is the user's LOCAL time: 07:00 in
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
    void preview_and_apply_materialize_three_contiguous_cells_as_one_long_entry() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        ImportPreviewResponse preview = preview(authed, contiguousSchedule("Java"));

        assertThat(preview.totalCells()).isEqualTo(3);
        assertThat(preview.timeEntriesToCreate()).isEqualTo(1);

        ImportApplyResponse applied = apply(authed, contiguousSchedule("Java"));

        assertThat(applied.created()).isEqualTo(1);
        Map<?, ?> run = authed.get()
                .uri("/api/v1/import/xlsx/{id}", applied.importRunId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(run.get("totalCells")).isEqualTo(3);
        assertThat(run.get("mapped")).isEqualTo(3);
        assertThat(run.get("unknown")).isEqualTo(0);
        assertThat(timeEntryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStartAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0));
            assertThat(entry.getEndAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 45));
            assertThat(entry.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(entry.getDelo().getId()).isEqualTo(coding.getId());
        });
    }

    @Test
    void skip_all_splits_a_long_import_interval_around_an_occupied_middle_slot() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());
        LocalDateTime middle = LocalDateTime.of(2026, 6, 1, 9, 15);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(existing).startAt(middle).endAt(middle.plusMinutes(15))
                .status(TimeEntry.Status.DONE).build());

        ImportApplyResponse applied = apply(authed, contiguousSchedule("Java"));

        assertThat(applied.created()).isEqualTo(2);
        assertThat(applied.skippedOccupied()).isEqualTo(1);
        assertThat(timeEntryRepository.findAll()).hasSize(3);
        assertThat(timeEntryRepository.findByUserIdAndStartAt(admin.getId(), LocalDateTime.of(2026, 6, 1, 9, 0)))
                .get().satisfies(entry -> assertThat(entry.getEndAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 15)));
        assertThat(timeEntryRepository.findByUserIdAndStartAt(admin.getId(), LocalDateTime.of(2026, 6, 1, 9, 30)))
                .get().satisfies(entry -> assertThat(entry.getEndAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 45)));
    }

    @Test
    void overwrite_all_merges_a_replaced_middle_slot_into_one_long_import_interval() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());
        LocalDateTime middle = LocalDateTime.of(2026, 6, 1, 9, 15);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(existing).startAt(middle).endAt(middle.plusMinutes(15))
                .status(TimeEntry.Status.DONE).build());

        ImportApplyResponse applied = applyWithStrategy(authed, contiguousSchedule("Java"), "OVERWRITE_ALL");

        assertThat(applied.created()).isEqualTo(1);
        assertThat(applied.overwritten()).isEqualTo(1);
        assertThat(timeEntryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStartAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0));
            assertThat(entry.getEndAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 45));
            assertThat(entry.getDelo().getId()).isEqualTo(coding.getId());
        });
    }

    @Test
    void overwrite_all_preserves_unimported_fragments_of_an_existing_long_entry() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        Delo existing = deloRepository.save(Delo.builder().user(admin).title("Уже было").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());
        LocalDateTime existingStart = LocalDateTime.of(2026, 6, 1, 9, 0);
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(existing).startAt(existingStart).endAt(existingStart.plusHours(1))
                .status(TimeEntry.Status.DONE).build());

        ImportApplyResponse applied = applyWithStrategy(authed, singleCellSchedule("Java", 9, 15), "OVERWRITE_ALL");

        assertThat(applied.created()).isEqualTo(1);
        assertThat(applied.overwritten()).isEqualTo(1);
        assertThat(timeEntryRepository.findAll()).hasSize(3);
        assertThat(timeEntryRepository.findByUserIdAndStartAt(admin.getId(), LocalDateTime.of(2026, 6, 1, 9, 0)))
                .get().satisfies(entry -> assertThat(entry.getEndAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 15)));
        assertThat(timeEntryRepository.findByUserIdAndStartAt(admin.getId(), LocalDateTime.of(2026, 6, 1, 9, 15)))
                .get().satisfies(entry -> {
                    assertThat(entry.getEndAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 30));
                    assertThat(entry.getDelo().getId()).isEqualTo(coding.getId());
                });
        assertThat(timeEntryRepository.findByUserIdAndStartAt(admin.getId(), LocalDateTime.of(2026, 6, 1, 9, 30)))
                .get().satisfies(entry -> assertThat(entry.getEndAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0)));
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
    void case_and_whitespace_variants_share_one_activity_and_existing_mapping() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java   Script").delo(coding).build());

        ImportPreviewResponse preview = preview(authed,
                scheduleWithActivities(" Java Script ", "JAVA SCRIPT", "java script"));

        assertThat(preview.knownActivities()).isEqualTo(1);
        assertThat(preview.newActivities()).isZero();
        assertThat(preview.activities()).singleElement().satisfies(activity -> {
            assertThat(activity.activityText()).isEqualTo("Java Script");
            assertThat(activity.cells()).isEqualTo(3);
            assertThat(activity.known()).isTrue();
            assertThat(activity.deloTitle()).isEqualTo("Программирование");
        });

        ImportApplyResponse applied = apply(authed, scheduleWithActivities(" Java Script ", "JAVA SCRIPT", "java script"));
        assertThat(applied.pendingQuestions()).isZero();
        assertThat(questionRepository.count()).isZero();
        assertThat(timeEntryRepository.findAll()).hasSize(3).allSatisfy(entry -> {
            assertThat(entry.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(entry.getDelo().getId()).isEqualTo(coding.getId());
        });
    }

    @Test
    void semantic_variant_stays_unknown_until_user_confirms_existing_delo_mapping() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();
        Delo gym = deloRepository.save(Delo.builder().user(admin).title("Спортзал").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Спортзал").delo(gym).build());
        byte[] bytes = scheduleWithActivities("в спортзал");

        ImportPreviewResponse preview = preview(authed, bytes);
        assertThat(preview.knownActivities()).isZero();
        assertThat(preview.newActivities()).isEqualTo(1);
        assertThat(preview.activities()).singleElement()
                .satisfies(activity -> assertThat(activity.known()).isFalse());

        ImportApplyResponse applied = apply(authed, bytes);
        assertThat(applied.pendingQuestions()).isEqualTo(1);
        TimeEntry unknown = timeEntryRepository.findAll().get(0);
        assertThat(unknown.getStatus()).isEqualTo(TimeEntry.Status.UNKNOWN);
        assertThat(unknown.getDelo()).isNull();

        resolve(authed, applied.importRunId(), "в спортзал", gym.getId());

        assertThat(mappingRepository.findByUserAndNormalizedActivityText(admin, "в спортзал"))
                .get().extracting(ActivityMapping::getDelo).extracting(Delo::getId).isEqualTo(gym.getId());
        TimeEntry resolved = timeEntryRepository.findAll().get(0);
        assertThat(resolved.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(resolved.getDelo().getId()).isEqualTo(gym.getId());
    }

    @Test
    void different_meanings_are_not_collapsed_by_automatic_normalization() throws Exception {
        WebTestClient authed = authedAdminClient();
        ImportPreviewResponse preview = preview(authed,
                scheduleWithActivities("Проект", "Проект игры"));

        assertThat(preview.activities()).extracting(ImportPreviewResponse.ActivityPreview::activityText)
                .containsExactlyInAnyOrder("Проект", "Проект игры");
        assertThat(preview.newActivities()).isEqualTo(2);
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

    /**
     * Ticket 04 — resolving the unknown activity «Сон» (any case) reuses the existing Дело of the
     * «Ночные часы» mechanism instead of creating a duplicate, and marks it supporting.
     */
    @Test
    void resolving_sleep_uses_existing_sleep_delo_without_duplicate() throws Exception {
        WebTestClient authed = authedAdminClient();
        // Pre-existing sleep Дело from the «Ночные часы» mechanism (Т-1).
        Delo preexistingSleep = deloRepository.save(Delo.builder()
                .user(admin()).title("Сон").build());

        // A schedule whose only unknown activity is «Сон» (mixed case to prove normalization).
        byte[] bytes = scheduleWith(ActivityMapping.builder().user(admin()).activityText("Java")
                .delo(deloRepository.save(Delo.builder().user(admin()).title("Программирование").build())).build(),
                "сон");
        apply(authed, bytes);

        // Resolve «сон» via the question flow, asking to create a new Дело.
        long runId = runRepository.findAll().get(0).getId();
        authed.post()
                .uri("/api/v1/import/xlsx/{id}/resolve", runId)
                .bodyValue(new XlsxImportService.ResolveRequest("сон", null,
                        new XlsxImportService.CreateDelo("сон", null, null)))
                .exchange()
                .expectStatus().isOk();

        // Still exactly one «Сон» Дело — the pre-existing one, reused, now flagged supporting.
        assertThat(deloRepository.findByUserAndTitleInIgnoreCase(admin(),
                List.of("сон".toLowerCase()))).hasSize(1);
        Delo sleep = deloRepository.findByUserAndTitleInIgnoreCase(admin(),
                List.of("сон".toLowerCase())).get(0);
        assertThat(sleep.getId()).isEqualTo(preexistingSleep.getId());
        assertThat(sleep.isSupporting()).isTrue();

        // The «Сон» Записи времени are now DONE and linked to that одному Делу (use a repo query with
        // the known delo id to avoid lazy-loading the Delo proxy outside a session).
        var sleepEntries = timeEntryRepository.findByUserAndDeloAndStartAtBetween(
                admin().getId(), preexistingSleep.getId(),
                LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(2100, 1, 1, 0, 0));
        assertThat(sleepEntries).isNotEmpty();
        assertThat(sleepEntries).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(e.getDelo().getId()).isEqualTo(preexistingSleep.getId());
        });
    }

    /**
     * Ticket 04 — a class-1 activity (Еда) resolves to a Дело flagged {@code supporting=true},
     * while a work Дело (Java) stays non-supporting.
     */
    @Test
    void resolving_supporting_activity_marks_delo_supporting() throws Exception {
        WebTestClient authed = authedAdminClient();
        byte[] bytes = scheduleWith(
                deloRepository.save(Delo.builder().user(admin()).title("Программирование").build())
                        .getId(),
                "Java", "Еда");
        apply(authed, bytes);

        long runId = runRepository.findAll().get(0).getId();
        for (String activity : List.of("Java", "Еда")) {
            authed.post()
                    .uri("/api/v1/import/xlsx/{id}/resolve", runId)
                    .bodyValue(new XlsxImportService.ResolveRequest(activity, null,
                            new XlsxImportService.CreateDelo(activity, null, null)))
                    .exchange()
                    .expectStatus().isOk();
        }

        Delo eda = deloRepository.findByUserAndTitleInIgnoreCase(admin(), List.of("еда")).get(0);
        assertThat(eda.isSupporting()).isTrue();
        Delo java = deloRepository.findByUserAndTitleInIgnoreCase(admin(), List.of("java")).get(0);
        assertThat(java.isSupporting()).isFalse();
    }

    /** Builds one Monday with a single 15-minute cell. */
    private byte[] singleCellSchedule(String activity, int hour, int minute) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row row = sheet.createRow(2);
            row.createCell(2).setCellValue(timeFraction(hour, minute));
            row.createCell(3).setCellValue(activity);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Builds one Monday with three consecutive 15-minute cells for one activity. */
    private byte[] contiguousSchedule(String activity) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            for (int slot = 0; slot < 3; slot++) {
                Row row = sheet.createRow(2 + slot);
                row.createCell(2).setCellValue(timeFraction(9, slot * 15));
                row.createCell(3).setCellValue(activity);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Builds a one-week schedule with one row containing the supplied activity variants. */
    private byte[] scheduleWithActivities(String... activities) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row row = sheet.createRow(2);
            row.createCell(2).setCellValue(timeFraction(7, 0));
            for (int index = 0; index < activities.length && index < 7; index++) {
                row.createCell(3 + index).setCellValue(activities[index]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Builds a one-week schedule (1 June 07:00) with the given known mapping and unknown activities. */
    private byte[] scheduleWith(ActivityMapping known, String... unknownActivities) throws Exception {
        Delo knownDelo = known.getDelo();
        mappingRepository.save(known);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row row = sheet.createRow(2);
            row.createCell(2).setCellValue(timeFraction(7, 0));
            row.createCell(3).setCellValue(knownDelo.getTitle()); // known
            int col = 4;
            for (String activity : unknownActivities) {
                if (col > 9) break;
                row.createCell(col++).setCellValue(activity); // unknown → question
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Builds a one-week schedule where the given activityText → existing Дело (pre-seeded mapping). */
    private byte[] scheduleWith(Long knownDeloId, String knownActivity, String... unknownActivities) throws Exception {
        Delo knownDelo = deloRepository.findById(knownDeloId).orElseThrow();
        mappingRepository.save(ActivityMapping.builder()
                .user(admin()).activityText(knownActivity).delo(knownDelo).build());
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row row = sheet.createRow(2);
            row.createCell(2).setCellValue(timeFraction(7, 0));
            row.createCell(3).setCellValue(knownActivity);
            int col = 4;
            for (String activity : unknownActivities) {
                if (col > 9) break;
                row.createCell(col++).setCellValue(activity);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Resolve helper that posts a question-resolution request. */
    private void resolve(WebTestClient client, long runId, String activity, Long deloId) {
        client.post()
                .uri("/api/v1/import/xlsx/{id}/resolve", runId)
                .bodyValue(new XlsxImportService.ResolveRequest(activity, deloId,
                        new XlsxImportService.CreateDelo(activity, null, null)))
                .exchange()
                .expectStatus().isOk();
    }
}
