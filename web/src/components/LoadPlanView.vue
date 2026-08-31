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
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->
<script setup>
/*
 * Страница «План нагрузки» (release 1.1, тикет 02).
 * Прежде «План нагрузки» был секцией-якорем внутри Дорожной карты (/roadmap#load-plan);
 * решение владельца B — вынести в самостоятельный маршрут /load-plan (старый якорь → 302,
 * см. навигационный guard в main.js). Тонкая обёртка-страница: заголовок + LoadCharts
 * (четыре вкладки над единым источником данных load-charts; заголовок-секция внутри
 * LoadCharts убран, чтобы не было двойной шапки).
 */
import LoadCharts from './LoadCharts.vue'
</script>

<template>
  <div class="load-plan-page">
    <header class="page-header load-plan-header">
      <div>
        <h1>План нагрузки</h1>
        <p class="eyebrow">Планирование · нагрузка по Проектам и Рутинам</p>
      </div>
    </header>
    <LoadCharts />
  </div>
</template>

<style scoped>
.load-plan-page { width: 100%; }
</style>
