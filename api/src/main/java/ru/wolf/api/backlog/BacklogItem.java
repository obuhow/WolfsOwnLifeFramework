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
package ru.wolf.api.backlog;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "backlog_item", uniqueConstraints = @UniqueConstraint(name = "uq_backlog_item_period", columnNames = {"user_id", "delo_id", "scope", "period_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BacklogItem {
    public enum Scope { WEEK, MONTH }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "delo_id") private Delo delo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Scope scope;
    @Column(name = "period_id", nullable = false, length = 10) private String periodId;
    @Column(name = "planned_hours", precision = 6, scale = 2) private BigDecimal plannedHours;
    @Column(nullable = false) private Integer position;
    @Column(name = "moved_to_week", length = 10) private String movedToWeek;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); if (position == null) position = 0; }
}
