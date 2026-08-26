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

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "xlsx_import_run", uniqueConstraints = @UniqueConstraint(name = "uk_xlsx_import_user_hash", columnNames = {"user_id", "file_hash"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class XlsxImportRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private String filename;
    @Column(name = "file_hash", nullable = false, length = 128) private String fileHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int totalCells;
    @Column(nullable = false) private int mapped;
    @Column(nullable = false) private int unknown;
    @Column(nullable = false) private int pendingQuestions;
    @Column(nullable = false) private Instant createdAt;

    public enum Status { PAUSED, DONE }
    @PrePersist void created() { createdAt = Instant.now(); }
}

