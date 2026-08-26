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
package ru.wolf.api.note;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "note")
@Check(name = "ck_note_exactly_one_parent", constraints = "(project_id IS NOT NULL AND delo_id IS NULL) OR (project_id IS NULL AND delo_id IS NOT NULL)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delo_id")
    private Delo delo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Author author = Author.USER;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private String[] tags = new String[0];

    @OneToOne(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private NoteAttachment audioAttachment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Author {
        USER,
        AGENT
    }
}

// Note: exactly one of project and delo is enforced both here and by the database migration.
