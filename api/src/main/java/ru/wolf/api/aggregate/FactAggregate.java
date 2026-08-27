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
package ru.wolf.api.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fact-hours aggregate for Project / Delo detail cards (ticket 13).
 *
 * <p>Project aggregates follow the same attribution rules as Gantt fact
 * ({@code hourAccountingMode}, DONE only, no ad-hoc). Delo aggregates sum
 * DONE entries of that Дело only (no project attribution needed).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactAggregate {

    /** Sum of byDay hours. */
    private BigDecimal totalFactHours;

    /**
     * Accounting mode applied when computing project aggregates.
     * For Delo aggregates this is informational (user setting), attribution N/A.
     */
    private String hourAccountingMode;

    /** Per logical day (user dayEnd), ascending by date. Empty when no fact. */
    private List<DayHours> byDay = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayHours {
        /** Logical day as YYYY-MM-DD. */
        private String date;
        private BigDecimal hours;
    }
}
