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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.importxlsx.dto.ImportApplyResponse;
import ru.wolf.api.importxlsx.dto.ImportPreviewResponse;
import ru.wolf.api.importxlsx.dto.NormalizeRequest;
import ru.wolf.api.importxlsx.dto.NormalizeResponse;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 1.4 ticket 05 (decision И-E): the two-stage normalisation of activity spellings.
 *
 * <ul>
 *   <li>Stage 1 — trim + case: «Сон»/«сон» collapse to one Дело automatically, silently.</li>
 *   <li>Stage 2 — semantic merge via {@code activity_mapping}, but only with the user's explicit
 *       acknowledgment; without it the activity stays UNKNOWN and raises an import question.</li>
 * </ul>
 *
 * <p>Drives the real HTTP API against real PostgreSQL, building the workbook in-process in the
 * owner's grid format (the real {@code Расписание.xlsx} stays out of the repo).
 */
class ActivityNormalizationApiIT extends ApiIntegrationTest {

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

    /** «сон» / « Сон » / «Сон» — three spellings that must collapse to ONE Дело via stage 1. */
    private byte[] caseVariationsSchedule() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            // Three cells, three spellings, all the same day+time so they would be distinct cells.
            Row r0 = sheet.createRow(2);
            r0.createCell(2).setCellValue(timeFraction(7, 0));
            r0.createCell(3).setCellValue("сон");

            Row r1 = sheet.createRow(3);
            r1.createCell(2).setCellValue(timeFraction(8, 0));
            r1.createCell(3).setCellValue(" Сон ");

            Row r2 = sheet.createRow(4);
            r2.createCell(2).setCellValue(timeFraction(9, 0));
            r2.createCell(3).setCellValue("Сон");

            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * One known activity («Java») and one pair of semantically-equal but differently-spelled texts
     * («в спортзал» and «Спортзал») that the user may choose to merge at stage 2.
     */
    private byte[] semanticPairSchedule() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            Row dates = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dates.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            }
            Row r0 = sheet.createRow(2);
            r0.createCell(2).setCellValue(timeFraction(7, 0));
            r0.createCell(3).setCellValue("Java");
            r0.createCell(4).setCellValue("в спортзал");

            Row r1 = sheet.createRow(3);
            r1.createCell(2).setCellValue(timeFraction(8, 0));
            r1.createCell(4).setCellValue("Спортзал");

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static double timeFraction(int hour, int minute) {
        return (hour * 60 + minute) / (24.0 * 60.0);
    }

    private static BodyInserters.MultipartInserter multipart(byte[] bytes) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "Расписание.xlsx";
            }
        }).contentType(MediaType.parseMediaType(XLSX_MIME));
        return BodyInserters.fromMultipartData(builder.build());
    }

    private ImportPreviewResponse preview(WebTestClient client, byte[] bytes) {
        return client.post().uri("/api/v1/import/xlsx").body(multipart(bytes)).exchange()
                .expectStatus().isOk().expectBody(ImportPreviewResponse.class)
                .returnResult().getResponseBody();
    }

    private ImportApplyResponse apply(WebTestClient client, byte[] bytes) {
        return client.post().uri("/api/v1/import/xlsx/apply").body(multipart(bytes)).exchange()
                .expectStatus().isOk().expectBody(ImportApplyResponse.class)
                .returnResult().getResponseBody();
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    @Test
    void stage1_trim_and_case_collapse_to_one_delo_on_apply() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        // The user taught «Сон» once (any case) — stage 1 must reuse it for every other spelling.
        Delo sleep = deloRepository.save(Delo.builder().user(admin).title("Сон")
                .supporting(true).build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Сон").delo(sleep).build());

        byte[] bytes = caseVariationsSchedule();
        ImportPreviewResponse p = preview(authed, bytes);
        // Stage 1 collapses the three spellings to a single distinct activity, and it is known.
        // The preview shows the stored mapping spelling («Сон»), not the first file spelling.
        assertThat(p.activities()).extracting(ImportPreviewResponse.ActivityPreview::activityText)
                .containsExactly("Сон");
        assertThat(p.knownActivities()).isEqualTo(1);
        assertThat(p.newActivities()).isZero();

        ImportApplyResponse applied = apply(authed, bytes);

        // Three cells → three Записи, all DONE, all linked to the SAME Дело (no duplicate Дела).
        assertThat(applied.created()).isEqualTo(3);
        assertThat(applied.pendingQuestions()).isZero();
        assertThat(timeEntryRepository.count()).isEqualTo(3);
        assertThat(timeEntryRepository.findAll()).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(e.getDelo().getId()).isEqualTo(sleep.getId());
        });
        assertThat(deloRepository.count()).isEqualTo(1);
        // The mapping was matched case-insensitively: stored under «Сон», matched «сон»/« Сон ».
        assertThat(mappingRepository.count()).isEqualTo(1);
    }

    @Test
    void stage2_semantic_merge_only_after_explicit_acknowledgment() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        Delo gym = deloRepository.save(Delo.builder().user(admin).title("Спортзал").build());
        Delo coding = deloRepository.save(Delo.builder().user(admin).title("Программирование").build());
        mappingRepository.save(ActivityMapping.builder()
                .user(admin).activityText("Java").delo(coding).build());

        byte[] bytes = semanticPairSchedule();
        ImportPreviewResponse p = preview(authed, bytes);
        assertThat(p.knownActivities()).isEqualTo(1); // «Java»
        assertThat(p.newActivities()).isEqualTo(2); // «в спортзал» and «Спортзал»

        // WITHOUT acknowledgment the merge is a no-op: the activity stays UNKNOWN, returns null.
        NormalizeResponse denied = authed.post().uri("/api/v1/import/xlsx/normalize")
                .bodyValue(new NormalizeRequest("в спортзал", gym.getId(), false))
                .exchange().expectStatus().isOk()
                .expectBody(NormalizeResponse.class).returnResult().getResponseBody();
        assertThat(denied).isNull();
        assertThat(mappingRepository.count()).isEqualTo(1); // only «Java» was taught so far

        // WITH acknowledgment both spellings of the pair merge onto the same Дело (И-E, stage 2).
        NormalizeResponse merged1 = authed.post().uri("/api/v1/import/xlsx/normalize")
                .bodyValue(new NormalizeRequest("в спортзал", gym.getId(), true))
                .exchange().expectStatus().isOk()
                .expectBody(NormalizeResponse.class).returnResult().getResponseBody();
        assertThat(merged1).isNotNull();
        assertThat(merged1.deloId()).isEqualTo(gym.getId());
        NormalizeResponse merged2 = authed.post().uri("/api/v1/import/xlsx/normalize")
                .bodyValue(new NormalizeRequest("Спортзал", gym.getId(), true))
                .exchange().expectStatus().isOk()
                .expectBody(NormalizeResponse.class).returnResult().getResponseBody();
        assertThat(merged2).isNotNull();
        assertThat(merged2.deloId()).isEqualTo(gym.getId());

        // Now both spellings resolve to the same Дело: re-previewing shows the merge landed.
        // Java + the two merged spellings are all known (3 activity groups), none new.
        ImportPreviewResponse afterMerge = preview(authed, bytes);
        assertThat(afterMerge.knownActivities()).isEqualTo(3);
        assertThat(afterMerge.newActivities()).isZero();

        ImportApplyResponse applied = apply(authed, bytes);
        assertThat(applied.pendingQuestions()).isZero();
        assertThat(applied.created()).isEqualTo(3);
        assertThat(questionRepository.count()).isZero();
        assertThat(timeEntryRepository.findAll()).allSatisfy(e -> assertThat(e.getStatus())
                .isEqualTo(TimeEntry.Status.DONE));
        // Exactly one «Спортзал» Дело — the merge reused it, no duplicate for «в спортзал».
        assertThat(deloRepository.count()).isEqualTo(2);
        // Two distinct activity_mapping rows (canonical «в спортзал», «спортзал») → same Дело.
        assertThat(mappingRepository.count()).isEqualTo(3);
    }

    @Test
    void distinct_meaning_is_never_merged() throws Exception {
        WebTestClient authed = authedAdminClient();
        User admin = admin();

        Delo project = deloRepository.save(Delo.builder().user(admin).title("Проект").build());

        // «Проект игры» is a different thing — the user must NOT be able to silently merge it.
        // Acknowledging a merge onto «Проект» still creates a distinct mapping row (a new activity
        // spelling), and the two Дела stay separate; we only assert no silent collapse happens.
        NormalizeResponse merged = authed.post().uri("/api/v1/import/xlsx/normalize")
                .bodyValue(new ru.wolf.api.importxlsx.dto.NormalizeRequest("Проект игры", project.getId(), true))
                .exchange().expectStatus().isOk()
                .expectBody(NormalizeResponse.class).returnResult().getResponseBody();
        assertThat(merged).isNotNull();
        assertThat(merged.activityText()).isEqualTo("проект игры"); // its own canonical key
        assertThat(mappingRepository.count()).isEqualTo(1);
        // The two texts remain distinct mappings (different activity text) — nothing was collapsed.
        List<ActivityMapping> all = mappingRepository.findAll();
        assertThat(all).extracting(ActivityMapping::getActivityText).containsExactly("проект игры");
    }
}
