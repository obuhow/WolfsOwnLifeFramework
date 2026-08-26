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
package ru.wolf.api.lifesphere;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "synergy", uniqueConstraints = {
        @UniqueConstraint(name = "uk_synergy_project_sphere", columnNames = {"project_id", "sphere_id"}),
        @UniqueConstraint(name = "uk_synergy_idea_sphere", columnNames = {"idea_id", "sphere_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Synergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ru.wolf.api.user.User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ru.wolf.api.project.Project project;

    @Column(name = "idea_id")
    private Long ideaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private ru.wolf.api.routine.Routine routine;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sphere_id", nullable = false)
    private LifeSphere sphere;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 10)
    private Impact impact;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Impact {
        POSITIVE,   // +1
        NEGATIVE,   // -1
        NEUTRAL     // 0
    }
}
