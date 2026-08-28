<!--
  WOLF — Wolf's Own Life Framework
  Copyright (C) 2025 Pavel Obukhov

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. if not see <https://www.gnu.org/licenses/>.
-->
<script setup>
/*
 * Страница «Дорожная карта». Верхняя часть — Гантт (GanttView).
 * Секция «План нагрузки» (#load-plan) целиком вынесена в LoadCharts.vue (release 0.8,
 * тикет 02): четыре вкладки диаграмм над единым источником данных load-charts.
 * Старые плоские таблицы capacity-list / curve-list удалены — их данные полностью покрыты
 * новым эндпоинтом.
 */
import GanttView from './GanttView.vue'
import LoadCharts from './LoadCharts.vue'
</script>

<template>
  <div class="planning-page">
    <GanttView />
    <LoadCharts />
  </div>
</template>

<style scoped>
.planning-page { width: 100%; }
</style>
