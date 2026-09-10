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
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.importxlsx.dto.ImportApplyResponse;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.UserRepository;

/**
 * Release 1.4 ticket 02 — the run-level side of the import: the questions raised for unknown
 * activities, and learning a mapping through {@code /resolve}.
 *
 * <p>Rewritten for the owner's actual grid format. The previous version of this test drove the old
 * {@code POST /import/xlsx} that wrote immediately, in a format the owner's file does not use
 * (sheet named {@code 2026-04-06}, activities in columns A–B, one-row header, slots from 00:00) —
 * that parser and that behaviour are gone, see {@link XlsxImportService}.
 */
class XlsxImportApiIT extends ApiIntegrationTest {
    @Autowired TimeEntryRepository entries;
    @Autowired DeloRepository delos;
    @Autowired XlsxImportRunRepository runs;
    @Autowired XlsxImportQuestionRepository questions;
    @Autowired ActivityMappingRepository mappings;
    @Autowired UserRepository users;

    private static final int SERIAL_2026_06_01 = 46174;

    @BeforeEach
    void cleanup() {
        questions.deleteAll(); mappings.deleteAll(); entries.deleteAll(); runs.deleteAll(); delos.deleteAll();
    }

    @Test
    void unknown_activity_pauses_and_resolve_creates_done_entry() throws Exception {
        WebTestClient client = authedAdminClient();
        Delo known = delos.save(Delo.builder().user(users.findByUsername("admin").orElseThrow()).title("Известно").build());
        mappings.save(ActivityMapping.builder().user(users.findByUsername("admin").orElseThrow())
                .activityText("Известная").delo(known).build());

        ImportApplyResponse applied = apply(client, workbook("Известная", "Неизвестная"));

        assertThat(applied.pendingQuestions()).isEqualTo(1);
        Map run = client.get().uri("/api/v1/import/xlsx/{id}", applied.importRunId())
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(run.get("status")).isEqualTo("PAUSED");

        Map resolved = client.post().uri("/api/v1/import/xlsx/{id}/resolve", applied.importRunId())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("activityText", "Неизвестная", "deloId", known.getId()))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(resolved.get("status")).isEqualTo("DONE");
        Long adminId = users.findByUsername("admin").orElseThrow().getId();
        // Read through the fetch-join query: the plain findAll would hand back lazy Delo proxies
        // detached from any session, and getTitle() would blow up outside the transaction.
        var afterResolve = entries.findByUserIdAndStartAtBetween(adminId,
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0), java.time.LocalDateTime.of(2026, 6, 8, 0, 0));
        assertThat(afterResolve).hasSize(2);
        assertThat(afterResolve).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(e.getDelo().getTitle()).isEqualTo("Известно");
        });
    }

    /**
     * Decision И-C: a blank cell is nothing recorded, so it must NOT be materialised as an
     * {@code UNKNOWN} Запись. The old import created one per blank cell; that is what made a
     * half-empty week look like a half-full one.
     */
    @Test
    void blank_cells_create_nothing_and_reimport_is_idempotent() throws Exception {
        WebTestClient client = authedAdminClient();
        byte[] bytes = workbook("", "");

        ImportApplyResponse applied = apply(client, bytes);

        assertThat(applied.created()).isZero();
        assertThat(applied.pendingQuestions()).isZero();
        assertThat(entries.count()).isZero();

        // Re-uploading the same file is still deduped by hash.
        ImportApplyResponse again = apply(client, bytes);
        assertThat(again.alreadyImported()).isTrue();
        assertThat(again.created()).isZero();
        assertThat(runs.count()).isEqualTo(1);
    }

    private ImportApplyResponse apply(WebTestClient client, byte[] bytes) {
        return client.post().uri("/api/v1/import/xlsx/apply").contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(new LinkedMultiValueMap<String, Object>() {{
                    add("file", new ByteArrayResource(bytes) { @Override public String getFilename() { return "Расписание.xlsx"; } });
                }}).exchange().expectStatus().isOk().expectBody(ImportApplyResponse.class).returnResult().getResponseBody();
    }

    /** A one-slot week in the owner's format: dates in row 1 (columns D–J), time in C, two days filled. */
    private byte[] workbook(String monday, String tuesday) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");
            var dateRow = sheet.createRow(0);
            for (int day = 0; day < 7; day++) dateRow.createCell(3 + day).setCellValue(SERIAL_2026_06_01 + day);
            var row = sheet.createRow(2);
            row.createCell(2).setCellValue((7 * 60) / (24.0 * 60.0));
            if (!monday.isEmpty()) row.createCell(3).setCellValue(monday);
            if (!tuesday.isEmpty()) row.createCell(4).setCellValue(tuesday);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
