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
package ru.wolf.api.checklist;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;
import java.time.*;

@Entity
@Table(name = "daily_checklist_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private LocalDate date;
    @Column(nullable = false, length = 500) private String title;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delo_id") private Delo delo;
    @Column(nullable = false) private Integer position;
    @Column(nullable = false) private boolean done;
    @Column(name = "done_at") private Instant doneAt;
    @PrePersist void init() { if (position == null) position = 0; }
}
