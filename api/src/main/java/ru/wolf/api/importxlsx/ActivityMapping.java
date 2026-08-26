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
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

@Entity
@Table(name = "activity_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "activity_text", nullable = false, length = 500) private String activityText;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "delo_id") private Delo delo;
}

