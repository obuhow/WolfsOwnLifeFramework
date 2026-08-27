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
package ru.wolf.api.gantt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeekPlanRepository extends JpaRepository<WeekPlan, Long> {

    @Query("""
            SELECT wp FROM WeekPlan wp
            JOIN FETCH wp.project
            WHERE wp.user = :user
              AND (
                    wp.isoYear > :fromYear
                    OR (wp.isoYear = :fromYear AND wp.isoWeek >= :fromWeek)
                  )
              AND (
                    wp.isoYear < :toYear
                    OR (wp.isoYear = :toYear AND wp.isoWeek <= :toWeek)
                  )
            """)
    List<WeekPlan> findInWeekRange(
            @Param("user") User user,
            @Param("fromYear") int fromYear,
            @Param("fromWeek") int fromWeek,
            @Param("toYear") int toYear,
            @Param("toWeek") int toWeek
    );

    Optional<WeekPlan> findByUserAndProjectIdAndIsoYearAndIsoWeek(
            User user, Long projectId, Integer isoYear, Integer isoWeek
    );

    void deleteByUserAndProjectIdAndIsoYearAndIsoWeek(
            User user, Long projectId, Integer isoYear, Integer isoWeek
    );

    /** Недельные планы всего профиля; см. {@code UserPurgeService}. */
    @Modifying
    @Query("DELETE FROM WeekPlan wp WHERE wp.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
