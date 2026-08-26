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
package ru.wolf.api.datasync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSyncContractTest {
    @Test
    void contract_exposes_version_manifest_and_all_supported_sheets() {
        DataSyncContract.Manifest manifest = DataSyncContract.manifest();

        assertThat(manifest.format()).isEqualTo("wolf-data");
        assertThat(manifest.version()).isEqualTo("0.21");
        assertThat(manifest.sheets()).extracting(DataSyncContract.Sheet::name)
                .containsExactly(
                        "life_areas", "life_spheres", "projects", "routines", "routine_schedules",
                        "delos", "time_entries", "goals", "goal_metrics", "goal_week_budgets", "ideas",
                        "notes", "synergies", "project_dependencies", "backlog_items", "checklist_items",
                        "activity_mappings");
        assertThat(manifest.sheets()).allSatisfy(sheet -> assertThat(sheet.columns()).contains("externalId"));
        assertThat(manifest.sheets().stream().filter(sheet -> sheet.name().equals("goals")).findFirst().orElseThrow().columns())
                .contains("projectExternalIds");
        assertThat(manifest.sheets().stream().filter(sheet -> sheet.name().equals("routines")).findFirst().orElseThrow().columns())
                .contains("goalExternalIds");
    }
}
