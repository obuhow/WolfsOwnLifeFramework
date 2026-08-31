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
 * Страница «Дорожная карта» (/roadmap). Теперь только Гантт (GanttView).
 * Секция «План нагрузки» (#load-plan) вынесена в самостоятельную страницу /load-plan
 * (release 1.1, тикет 02, решение владельца B). Здесь остаётся навигационная ссылка на
 * неё — без дублирования диаграмм. Старый якорь /roadmap#load-plan редиректит на
 * /load-plan (navigation guard в main.js).
 */
import GanttView from './GanttView.vue'
</script>

<template>
  <div class="planning-page">
    <GanttView />
    <p class="load-plan-link">
      <router-link to="/load-plan">Открыть План нагрузки →</router-link>
    </p>
  </div>
</template>

<style scoped>
.planning-page { width: 100%; }
.load-plan-link { margin: 24px 0 0; }
.load-plan-link a { color: var(--wolf-ink); text-decoration: none; border-bottom: 1px solid var(--wolf-rule); }
.load-plan-link a:hover { border-bottom-color: var(--wolf-ink); }
</style>
