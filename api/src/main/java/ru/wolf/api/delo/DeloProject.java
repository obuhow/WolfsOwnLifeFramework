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
package ru.wolf.api.delo;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.project.Project;

@Entity
@Table(name = "delo_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeloProject {

    @EmbeddedId
    private DeloProjectId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deloId")
    @JoinColumn(name = "delo_id", nullable = false)
    private Delo delo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;
}