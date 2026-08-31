/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package ru.wolf.api.datasync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.datasync.dto.ApplyRequest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@Service
@RequiredArgsConstructor
public class DataSyncControllerService {
    private final DataSyncImportService importService;
    private final DataSyncImportApplyService applyService;
    private final DataSyncExportService exportService;
    private final DataSyncCsvCodec csvCodec;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DataSyncImportService.PreviewResponse preview(String username, MultipartFile file) throws Exception {
        return importService.preview(currentUser(username), maybeConvertCsv(file));
    }

    /**
     * Если загружен наш CSV-набор (маркеры {@code # sheet:}), конвертирует его в
     * канонический xlsx-workbook, чтобы дальше работал единый импорт-пайплайн
     * (валидация + upsert). Обычный xlsx пропускается как есть.
     */
    private MultipartFile maybeConvertCsv(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        if (!csvCodec.looksLikeCsv(bytes)) {
            return file;
        }
        byte[] workbook = csvCodec.toWorkbook(bytes);
        return new ByteArrayMultipartFile(file.getName(), file.getOriginalFilename(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
    }

    public DataSyncImportService.PreviewResponse getPreview(String username, Long id) throws Exception {
        return importService.get(currentUser(username), id);
    }

    public DataSyncImportApplyService.ApplyResponse apply(String username, Long id, ApplyRequest request) throws Exception {
        User user = currentUser(username);
        return applyService.apply(user, id, request.checksum(), request.deleteMissing(), request.scopes());
    }

    public DataSyncImportApplyService.ApplyResponse result(String username, Long id) throws Exception {
        SyncImportPreview preview = importService.find(currentUser(username), id);
        if (preview.getResultJson() == null) {
            throw new IllegalArgumentException("Preview ещё не применён");
        }
        return objectMapper.readValue(preview.getResultJson(), DataSyncImportApplyService.ApplyResponse.class);
    }

    public DataSyncImportService.PreviewResponse plan(String username, Long id) throws Exception {
        return importService.get(currentUser(username), id);
    }

    public byte[] export(String username, String format, String version) throws Exception {
        validateExportFormat(format, version);
        User user = currentUser(username);
        if ("csv".equalsIgnoreCase(format)) {
            return csvCodec.toCsv(DataSyncContract.manifest(), exportService.buildRows(user));
        }
        return exportService.export(user);
    }

    public User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void validateExportFormat(String format, String version) {
        boolean supportedFormat = "xlsx".equalsIgnoreCase(format) || "csv".equalsIgnoreCase(format);
        if (!supportedFormat || !DataSyncContract.VERSION.equals(version)) {
            throw new IllegalArgumentException("Only XLSX/CSV data-sync version 0.21 is supported");
        }
    }
}
