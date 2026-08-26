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
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'
const weeks = ref(12), goals = ref([]), error = ref(''), loading = ref(false)
async function load() { loading.value = true; error.value = ''; try { const res = await fetch(`${apiBase()}/stats/wave?weeks=${weeks.value}`, { headers: authHeaders() }); if (!res.ok) throw new Error(`Статистика: HTTP ${res.status}`); goals.value = await res.json() } catch (e) { error.value = e.message } finally { loading.value = false } }
function width(v, goal) { const max = Math.max(...goal.weeks.map(w => Number(w.factHours)), 1); return `${Math.round(Number(v) / max * 100)}%` }
onMounted(load)
</script>
<template><div class="stats-page"><header class="page-header"><h1>Волна продуктивности</h1><p class="eyebrow">Фактические часы по Целям · без оценок и стриков</p><select v-model.number="weeks" @change="load"><option :value="4">4 недели</option><option :value="12">12 недель</option><option :value="26">26 недель</option></select></header><p v-if="error" class="banner error">{{ error }}</p><section v-for="goal in goals" :key="goal.goalId" class="card wave-card"><h2>{{ goal.title }}</h2><div class="bars"><div v-for="week in goal.weeks" :key="week.weekId" class="bar-row"><span>{{ week.weekId }}</span><div class="bar-track"><div class="bar" :style="{ width: width(week.factHours, goal) }"></div></div><strong>{{ week.factHours }} ч</strong></div></div><p class="muted">среднее {{ goal.avg.toFixed(1) }} ч · медиана {{ goal.median.toFixed(1) }} · максимум {{ goal.max.toFixed(1) }} · минимум {{ goal.min.toFixed(1) }}</p></section><p class="muted note">Волнообразная продуктивность — норма. Медленный старт → разгон → пауза.</p></div></template>
<style scoped>.stats-page{max-width:64rem}.wave-card{margin-top:1rem}.bars{display:grid;gap:.35rem}.bar-row{display:grid;grid-template-columns:6rem 1fr 4rem;gap:.6rem;align-items:center;font-size:.8rem}.bar-track{height:1.2rem;background:#e9eef5;border-radius:4px}.bar{height:100%;background:#6b86a8;border-radius:4px}.note{margin-top:1rem}</style>
