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
package ru.wolf.api.datasync;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "workbook_data", nullable = false, columnDefinition = "bytea")
    private byte[] workbookData;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(name = "errors_json", nullable = false, columnDefinition = "TEXT")
    private String errorsJson;
    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    private String planJson;
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
