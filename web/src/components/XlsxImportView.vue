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
import { onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'

const file = ref(null)
const run = ref(null)
const questions = ref([])
const loading = ref(false)
const error = ref('')
const resolved = ref('')

function headers(json = false) {
  const h = authHeaders(json)
  return h
}
function selectFile(event) { file.value = event.target.files[0] || null }
async function upload() {
  if (!file.value) { error.value = 'Выберите XLSX-файл'; return }
  loading.value = true; error.value = ''
  try {
    const data = new FormData(); data.append('file', file.value)
    const res = await fetch(`${apiBase()}/import/xlsx`, { method: 'POST', headers: headers(), body: data })
    if (!res.ok) throw new Error(`Импорт: HTTP ${res.status}`)
    run.value = await res.json(); await loadQuestions()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function loadQuestions() {
  if (!run.value) return
  const res = await fetch(`${apiBase()}/import/xlsx/${run.value.id}/questions`, { headers: headers() })
  if (res.ok) questions.value = await res.json()
}
async function resolve(question, deloId = null) {
  const title = window.prompt(`Создать Дело для «${question.activityText}»`, question.activityText)
  if (!title) return
  const res = await fetch(`${apiBase()}/import/xlsx/${run.value.id}/resolve`, {
    method: 'POST', headers: headers(true), body: JSON.stringify({ activityText: question.activityText, deloId, createDelo: { title, projectId: null } })
  })
  if (!res.ok) { error.value = `Разрешение: HTTP ${res.status}`; return }
  run.value = await res.json(); resolved.value = question.activityText; await loadQuestions()
}
onMounted(() => {})
</script>

<template>
  <div class="import-page">
    <header class="page-header"><h1>Импорт XLSX</h1><p class="eyebrow">Недельная сетка учёта времени · 15-минутные ячейки</p></header>
    <section class="card import-card">
      <label class="file-picker">Файл XLSX<input type="file" accept=".xlsx" @change="selectFile" /></label>
      <button class="btn btn-primary" :disabled="loading || !file" @click="upload">{{ loading ? 'Импорт…' : 'Загрузить и разобрать' }}</button>
      <p v-if="error" class="banner error">{{ error }}</p>
      <div v-if="run" class="import-progress"><strong>Статус: {{ run.status }}</strong><span>ячеек {{ run.totalCells }}</span><span>распознано {{ run.mapped }}</span><span>неизвестно {{ run.unknown }}</span><span>вопросов {{ run.pendingQuestions }}</span></div>
    </section>
    <section v-if="questions.length" class="card"><h2>Неизвестные активности</h2><div v-for="q in questions" :key="q.id" class="question-row"><span>{{ q.activityText }}</span><small>{{ q.startAt }}</small><button class="btn btn-ghost" @click="resolve(q)">Создать Дело и продолжить</button></div></section>
    <p v-if="resolved" class="banner ok">Активность «{{ resolved }}» добавлена в словарь.</p>
  </div>
</template>

<style scoped>
.import-card { display: grid; gap: 1rem; max-width: 48rem; }
.file-picker { display: grid; gap: .4rem; color: var(--muted-foreground, #756d64); }
.import-progress, .question-row { display: flex; flex-wrap: wrap; gap: .8rem; align-items: center; }
.question-row { justify-content: space-between; border-top: 1px solid var(--border, #e6dfd4); padding: .7rem 0; }
.question-row small { color: var(--muted-foreground, #756d64); }
</style>

",