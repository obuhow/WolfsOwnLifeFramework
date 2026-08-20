package ru.wolf.api.importxlsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.UserRepository;

class XlsxImportApiIT extends ApiIntegrationTest {
    @Autowired TimeEntryRepository entries;
    @Autowired DeloRepository delos;
    @Autowired XlsxImportRunRepository runs;
    @Autowired XlsxImportQuestionRepository questions;
    @Autowired ActivityMappingRepository mappings;
    @Autowired UserRepository users;

    @BeforeEach
    void cleanup() {
        questions.deleteAll(); mappings.deleteAll(); entries.deleteAll(); runs.deleteAll(); delos.deleteAll();
    }

    @Test
    void unknown_activity_pauses_and_resolve_creates_done_entry() throws Exception {
        WebTestClient client = authedAdminClient();
        Delo known = delos.save(Delo.builder().user(users.findByUsername("admin").orElseThrow()).title("Известно").build());
        mappings.save(ActivityMapping.builder().user(users.findByUsername("admin").orElseThrow())
                .activityText("Known").delo(known).build());
        XSSFWorkbook workbook = workbook("Known", "Unknown");
        Map result = upload(client, workbook);
        assertThat(result.get("status")).isEqualTo("PAUSED");
        assertThat(((Number) result.get("pendingQuestions")).intValue()).isEqualTo(1);
        Map resolved = client.post().uri("/api/v1/import/xlsx/{id}/resolve", result.get("id"))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("activityText", "Unknown", "deloId", known.getId()))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(resolved.get("status")).isEqualTo("DONE");
        assertThat(entries.findAll()).anySatisfy(e -> assertThat(e.getStatus()).isEqualTo(TimeEntry.Status.DONE));
    }

    @Test
    void blank_cell_is_unknown_and_reimport_is_idempotent() throws Exception {
        WebTestClient client = authedAdminClient();
        byte[] bytes;
        try (XSSFWorkbook workbook = workbook("", "")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        Map first = upload(client, bytes);
        long count = entries.count();
        Map second = upload(client, bytes);
        assertThat(entries.count()).isEqualTo(count);
        assertThat(first.get("status")).isEqualTo("DONE");
        assertThat(entries.findAll()).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(TimeEntry.Status.UNKNOWN));
    }

    private Map upload(WebTestClient client, XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); workbook.close();
        return upload(client, out.toByteArray());
    }

    private Map upload(WebTestClient client, byte[] bytes) {
        return client.post().uri("/api/v1/import/xlsx").contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(new org.springframework.util.LinkedMultiValueMap<String, Object>() {{
                    add("file", new ByteArrayResource(bytes) { @Override public String getFilename() { return "2026-04-06.xlsx"; } });
                }}).exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    private XSSFWorkbook workbook(String monday, String tuesday) {
        XSSFWorkbook wb = new XSSFWorkbook(); var sheet = wb.createSheet("2026-04-06");
        var row = sheet.createRow(1); row.createCell(0).setCellValue(monday); row.createCell(1).setCellValue(tuesday); return wb;
    }
}
