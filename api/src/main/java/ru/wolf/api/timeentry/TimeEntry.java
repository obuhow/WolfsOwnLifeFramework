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
package ru.wolf.api.timeentry;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Запись времени — continuous interval [startAt, endAt) on the grid (multiples of 15 min).
 * Empty cells are absence of a covering interval (status «неопределено»).
 */
@Entity
@Table(name = "time_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delo_id")
    private Delo delo;

    @Column(name = "ad_hoc_text", length = 500)
    private String adHocText;

    /** Inclusive start of the interval, wall-clock in user timezone (aligned to 15 min). */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** Exclusive end of the interval (aligned to 15 min), must be after startAt. */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status {
        /** запланирована */
        PLANNED,
        /** выполнена */
        DONE,
        /** ячейка импорта не содержит распознанного дела */
        UNKNOWN
    }

    public boolean isAdHoc() {
        return delo == null && adHocText != null && !adHocText.isBlank();
    }
}
