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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.wolf.api.user.User;

import java.util.List;

/**
 * Seeds the standard 9 Сферы жизни for a user. Reused by {@code DataInitializer} (admin),
 * registration (release 0.4 ticket 02) and the demo fixture generator (ticket 05) so the
 * canonical list lives in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class LifeSphereSeeder {

    private static final String[][] SPHERES = {
            {"Здоровье", "0", "#EF4444"},
            {"Навык QA Java", "1", "#3B82F6"},
            {"Навык музыканта", "2", "#8B5CF6"},
            {"Общение на расстоянии", "3", "#06B6D4"},
            {"Мотивация к делам", "4", "#F59E0B"},
            {"Ресурсы/деньги", "5", "#10B981"},
            {"Ресурсы/время", "6", "#84CC16"},
            {"Открытие новых ходов", "7", "#EC4899"},
            {"Независимость", "8", "#6366F1"}
    };

    private final LifeSphereRepository lifeSphereRepository;

    /** Idempotent: seeds only if the user has no Life Spheres yet. */
    public void seed(User user) {
        List<LifeSphere> existing = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);
        if (!existing.isEmpty()) {
            return;
        }
        for (String[] s : SPHERES) {
            LifeSphere sphere = LifeSphere.builder()
                    .user(user)
                    .name(s[0])
                    .sortOrder(Integer.parseInt(s[1]))
                    .color(s[2])
                    .build();
            lifeSphereRepository.save(sphere);
        }
    }
}
