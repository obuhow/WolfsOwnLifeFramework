package ru.wolf.api.datasync;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sync")
public class DataSyncManifestController {
    @GetMapping("/manifest")
    public ResponseEntity<DataSyncContract.Manifest> manifest(
            @RequestParam(defaultValue = DataSyncContract.VERSION) String version) {
        if (!DataSyncContract.VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported data-sync workbook version: " + version);
        }
        return ResponseEntity.ok(DataSyncContract.manifest());
    }
}
