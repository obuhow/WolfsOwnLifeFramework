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
package ru.wolf.api.focus;
import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import java.time.LocalDateTime;
@Entity @Table(name="focus_distraction") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FocusDistraction { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="focus_session_id") private FocusSession session; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delo_id") private Delo delo; private String text; @Column(name="at",nullable=false) private LocalDateTime at; private Integer minutes; @Column(name="applied_at") private LocalDateTime appliedAt; @Column(name="applied_minutes") private Integer appliedMinutes; }
