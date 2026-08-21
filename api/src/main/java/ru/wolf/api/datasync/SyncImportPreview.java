package ru.wolf.api.datasync;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "sync_import_preview", uniqueConstraints = @UniqueConstraint(
        name = "uk_sync_preview_user_checksum", columnNames = {"user_id", "checksum"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncImportPreview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 128)
    private String checksum;
    @Lob @Column(name = "workbook_data", nullable = false)
    private byte[] workbookData;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(name = "errors_json", nullable = false, columnDefinition = "TEXT")
    private String errorsJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;
    @Column(name = "applied_at")
    private Instant appliedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (expiresAt == null) expiresAt = createdAt.plusSeconds(86400);
    }
}
