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
package ru.wolf.api.loadcurve;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.routine.Routine;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "load_curve_entry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoadCurveEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "routine_id") private Routine routine;
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal hours;
}

