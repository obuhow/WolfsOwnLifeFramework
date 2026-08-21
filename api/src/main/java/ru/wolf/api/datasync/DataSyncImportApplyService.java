package ru.wolf.api.datasync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSyncImportApplyService {
    private final DataSyncImportService importService;
    private final SyncImportPreviewRepository previews;
    private final SyncExternalIdRepository externalIds;
    private final LifeAreaRepository lifeAreas;
    private final ProjectRepository projects;
    private final DeloRepository delos;
    private final DeloProjectRepository deloProjects;
    private final TimeEntryRepository timeEntries;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApplyResponse apply(User user, Long previewId, String checksum, boolean deleteMissing,
                               java.util.List<String> scopes) throws Exception {
        SyncImportPreview preview = importService.find(user, previewId);
        if (preview.getAppliedAt() != null) {
            if (!preview.getChecksum().equals(checksum)) throw new IllegalArgumentException("Checksum не совпадает с применённым preview");
            return objectMapper.readValue(preview.getResultJson(), ApplyResponse.class);
        }
        if (!"VALID".equals(preview.getStatus()) || !preview.getChecksum().equals(checksum)) {
            throw new IllegalArgumentException("Apply разрешён только для актуального валидного preview с тем же checksum");
        }
        if (deleteMissing && (scopes == null || scopes.isEmpty())) {
            throw new IllegalArgumentException("deleteMissing требует явного списка scopes");
        }
        Map<String, Integer> created = new HashMap<>();
        Map<String, Integer> updated = new HashMap<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(preview.getWorkbookData()))) {
            Map<String, LifeArea> areas = applyLifeAreas(user, workbook, created, updated);
            Map<String, Project> projectMap = applyProjects(user, workbook, areas, created, updated);
            Map<String, Delo> deloMap = applyDelos(user, workbook, projectMap, created, updated);
            applyTimeEntries(user, workbook, deloMap, created, updated);
        }
        ApplyResponse result = new ApplyResponse(previewId, created, updated, Map.of(), "APPLIED");
        preview.setAppliedAt(java.time.Instant.now());
        preview.setResultJson(objectMapper.writeValueAsString(result));
        preview.setStatus("APPLIED");
        previews.save(preview);
        return result;
    }

    private Map<String, LifeArea> applyLifeAreas(User user, Workbook workbook, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, LifeArea> result = new HashMap<>();
        var sheet = workbook.getSheet("life_areas");
        DataFormatter f = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); String externalId = f.formatCellValue(row.getCell(0));
            SyncExternalId identity = externalIds.findByUserAndEntityTypeAndExternalId(user, "life_area", externalId).orElse(null);
            LifeArea area;
            if (identity == null) { area = LifeArea.builder().user(user).name(f.formatCellValue(row.getCell(1))).sortOrder((int) row.getCell(2).getNumericCellValue()).color(f.formatCellValue(row.getCell(3))).build(); created.merge("life_areas", 1, Integer::sum); }
            else { area = lifeAreas.findByUserAndId(user, identity.getEntityId()).orElseThrow(); updated.merge("life_areas", 1, Integer::sum); }
            area.setName(f.formatCellValue(row.getCell(1))); area.setSortOrder((int) row.getCell(2).getNumericCellValue()); area.setColor(f.formatCellValue(row.getCell(3)));
            area = lifeAreas.save(area); bind(user, "life_area", area.getId(), externalId); result.put(externalId, area);
        }
        return result;
    }

    private Map<String, Project> applyProjects(User user, Workbook workbook, Map<String, LifeArea> areas, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Project> result = new HashMap<>(); DataFormatter f = new DataFormatter(); var sheet = workbook.getSheet("projects");
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); String externalId = f.formatCellValue(row.getCell(0)); String areaId = f.formatCellValue(row.getCell(1));
            LifeArea area = areas.get(areaId); if (area == null) throw new IllegalArgumentException("Неизвестная Область жизни: " + areaId);
            SyncExternalId identity = externalIds.findByUserAndEntityTypeAndExternalId(user, "project", externalId).orElse(null); Project p;
            if (identity == null) { p = Project.builder().user(user).lifeArea(area).title(f.formatCellValue(row.getCell(4))).build(); created.merge("projects", 1, Integer::sum); }
            else { p = projects.findByUserAndId(user, identity.getEntityId()).orElseThrow(); updated.merge("projects", 1, Integer::sum); }
            p.setLifeArea(area); p.setTitle(f.formatCellValue(row.getCell(4))); p.setDescription(f.formatCellValue(row.getCell(6))); p.setStatus(Project.Status.valueOf(f.formatCellValue(row.getCell(5))));
            p = projects.save(p); bind(user, "project", p.getId(), externalId); result.put(externalId, p);
        }
        return result;
    }

    private Map<String, Delo> applyDelos(User user, Workbook workbook, Map<String, Project> projectMap, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Delo> result = new HashMap<>(); DataFormatter f = new DataFormatter(); var sheet = workbook.getSheet("delos");
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); String externalId = f.formatCellValue(row.getCell(0)); SyncExternalId identity = externalIds.findByUserAndEntityTypeAndExternalId(user, "delo", externalId).orElse(null); Delo d;
            if (identity == null) { d = Delo.builder().user(user).title(f.formatCellValue(row.getCell(1))).build(); created.merge("delos", 1, Integer::sum); }
            else { d = delos.findByUserAndId(user, identity.getEntityId()).orElseThrow(); updated.merge("delos", 1, Integer::sum); }
            d.setTitle(f.formatCellValue(row.getCell(1))); d.setDescription(f.formatCellValue(row.getCell(2))); d.setExecutionMode(Delo.ExecutionMode.valueOf(f.formatCellValue(row.getCell(3))));
            d = delos.save(d); bind(user, "delo", d.getId(), externalId); result.put(externalId, d);
            String projectIds = f.formatCellValue(row.getCell(9));
            if (!projectIds.isBlank()) {
                for (String projectId : projectIds.split("\\|")) {
                    Project project = projectMap.get(projectId);
                    if (project != null && !deloProjects.existsByDeloAndProject(d, project)) {
                        deloProjects.save(DeloProject.builder().id(new ru.wolf.api.delo.DeloProjectId(d.getId(), project.getId())).delo(d).project(project)
                                .isPrimary(projectId.equals(f.formatCellValue(row.getCell(10)))).build());
                    }
                }
            }
        }
        return result;
    }

    private void applyTimeEntries(User user, Workbook workbook, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        DataFormatter f = new DataFormatter(); var sheet = workbook.getSheet("time_entries");
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); String externalId = f.formatCellValue(row.getCell(0)); SyncExternalId identity = externalIds.findByUserAndEntityTypeAndExternalId(user, "time_entry", externalId).orElse(null); TimeEntry entry;
            if (identity == null) { entry = TimeEntry.builder().user(user).build(); created.merge("time_entries", 1, Integer::sum); }
            else { entry = timeEntries.findById(identity.getEntityId()).orElseThrow(); updated.merge("time_entries", 1, Integer::sum); }
            entry.setDelo(delos.get(f.formatCellValue(row.getCell(1)))); entry.setAdHocText(f.formatCellValue(row.getCell(2))); entry.setStartAt(LocalDateTime.parse(f.formatCellValue(row.getCell(3)))); entry.setEndAt(LocalDateTime.parse(f.formatCellValue(row.getCell(4)))); entry.setStatus(TimeEntry.Status.valueOf(f.formatCellValue(row.getCell(5))));
            entry = timeEntries.save(entry); bind(user, "time_entry", entry.getId(), externalId);
        }
    }

    private void bind(User user, String type, Long entityId, String externalId) {
        externalIds.findByUserAndEntityTypeAndExternalId(user, type, externalId).ifPresentOrElse(identity -> identity.setEntityId(entityId), () -> externalIds.save(SyncExternalId.builder().user(user).entityType(type).entityId(entityId).externalId(externalId).build()));
    }

    public record ApplyResponse(Long previewId, Map<String, Integer> created, Map<String, Integer> updated, Map<String, Integer> deleted, String status) { }
}
