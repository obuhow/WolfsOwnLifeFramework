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
import { ref } from 'vue'
import { apiBase, authHeaders } from '../api'

const file = ref(null)
const preview = ref(null)
const applied = ref(null)
const questions = ref([])
const loading = ref(false)
const applying = ref(false)
const error = ref('')
const resolved = ref('')
const conflictChoice = ref('')

function headers(json = false) {
  return authHeaders(json)
}
function selectFile(event) {
  file.value = event.target.files[0] || null
  // A different file means the previous preview no longer describes what would be imported.
  preview.value = null
  applied.value = null
  questions.value = []
  resolved.value = ''
  conflictChoice.value = ''
  error.value = ''
}
function payload() {
  const data = new FormData()
  data.append('file', file.value)
  return data
}

/** Step 1 — parse and summarise. Deliberately writes nothing on the server. */
async function analyse() {
  if (!file.value) { error.value = 'Выберите XLSX-файл'; return }
  loading.value = true; error.value = ''; applied.value = null; resolved.value = ''
  try {
    const res = await fetch(`${apiBase()}/import/xlsx`, { method: 'POST', headers: headers(), body: payload() })
    if (!res.ok) throw new Error(`Разбор: HTTP ${res.status}`)
    preview.value = await res.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

/** Step 2 — the explicit apply. This is the only call that writes Записи времени. */
async function apply() {
  if (!file.value) return
  // When the preview shows conflicts, we ask ONCE for the whole import (decision И-G/H):
  // skip all / overwrite all / cancel. No per-cell questions.
  if (preview.value?.conflictingCells > 0 && !conflictChoice.value) {
    const ok = window.confirm(
      `Импорт пересекается с ${preview.value.conflictingCells} уже заполненными ячейками.\n` +
      '«Пропустить все» — оставить существующие записи, «Перезаписать все» — заменить их данными файла, «Отмена» — ничего не делать.'
    )
    if (!ok) { conflictChoice.value = 'CANCEL' }
    else {
      const overwrite = window.confirm('Заменить существующие записи данными файла? «ОК» — перезаписать, «Отмена» — пропустить.')
      conflictChoice.value = overwrite ? 'OVERWRITE_ALL' : 'SKIP_ALL'
    }
  }
  if (conflictChoice.value === 'CANCEL') {
    error.value = 'Импорт отменён: существующие записи не тронуты.'
    return
  }
  applying.value = true; error.value = ''
  try {
    const form = payload()
    if (conflictChoice.value) form.append('conflictStrategy', conflictChoice.value)
    const res = await fetch(`${apiBase()}/import/xlsx/apply`, { method: 'POST', headers: headers(), body: form })
    if (!res.ok) throw new Error(`Применение: HTTP ${res.status}`)
    applied.value = await res.json()
    await loadQuestions()
  } catch (e) { error.value = e.message } finally { applying.value = false }
}

async function loadQuestions() {
  if (!applied.value?.importRunId) return
  const res = await fetch(`${apiBase()}/import/xlsx/${applied.value.importRunId}/questions`, { headers: headers() })
  if (res.ok) questions.value = await res.json()
}

async function resolve(question, deloId = null) {
  const title = window.prompt(`Создать Дело для «${question.activityText}»`, question.activityText)
  if (!title) return
  const res = await fetch(`${apiBase()}/import/xlsx/${applied.value.importRunId}/resolve`, {
    method: 'POST', headers: headers(true), body: JSON.stringify({ activityText: question.activityText, deloId, createDelo: { title, projectId: null } })
  })
  if (!res.ok) { error.value = `Разрешение: HTTP ${res.status}`; return }
  resolved.value = question.activityText
  await loadQuestions()
}
</script>

<template>
  <div class="import-page">
    <header class="page-header"><h1>Импорт XLSX</h1><p class="eyebrow">Недельная сетка учёта времени · 15-минутные ячейки</p></header>

    <section class="card import-card">
      <label class="file-picker">Файл XLSX<input type="file" accept=".xlsx" @change="selectFile" /></label>
      <button class="btn btn-primary" :disabled="loading || !file" @click="analyse">{{ loading ? 'Разбор…' : 'Разобрать файл' }}</button>
      <p v-if="error" class="banner error">{{ error }}</p>
      <p class="hint">Файл только читается. Записи времени появятся после «Применить».</p>
    </section>

    <section v-if="preview" class="card">
      <h2>Предпросмотр</h2>
      <p v-if="preview.alreadyImported" class="banner warn">Этот файл уже импортирован — «Применить» не создаст дублей.</p>
      <div class="summary">
        <span><strong>{{ preview.weeks }}</strong> недель</span>
        <span><strong>{{ preview.totalCells }}</strong> заполненных ячеек</span>
        <span><strong>{{ preview.timeEntriesToCreate }}</strong> Записей будет создано</span>
        <span v-if="preview.from"><strong>{{ preview.from }}</strong> — <strong>{{ preview.to }}</strong></span>
        <span><strong>{{ preview.knownActivities }}</strong> известных активностей</span>
        <span><strong>{{ preview.newActivities }}</strong> новых</span>
        <span v-if="preview.conflictingCells"><strong>{{ preview.conflictingCells }}</strong> ячеек уже заняты — нужен выбор при «Применить»</span>
      </div>
      <details v-if="preview.activities?.length" class="activities">
        <summary>Активности ({{ preview.activities.length }})</summary>
        <table>
          <thead><tr><th>Активность</th><th>Ячеек</th><th>Дело</th></tr></thead>
          <tbody>
            <tr v-for="a in preview.activities" :key="a.activityText">
              <td>{{ a.activityText }}</td>
              <td class="num">{{ a.cells }}</td>
              <td><span v-if="a.known" class="chip done">{{ a.deloTitle }}</span><span v-else class="chip">новая</span></td>
            </tr>
          </tbody>
        </table>
      </details>
      <button class="btn btn-primary" :disabled="applying" @click="apply">{{ applying ? 'Применяю…' : 'Применить' }}</button>
    </section>

    <section v-if="applied" class="card">
      <h2>Импорт завершён</h2>
      <p v-if="applied.alreadyImported" class="banner warn">Файл уже был импортирован — ничего не создано.</p>
      <p v-if="applied.cancelled" class="banner warn">Импорт отменён — существующие записи не тронуты.</p>
      <div class="summary">
        <span><strong>{{ applied.created }}</strong> Записей создано</span>
        <span v-if="applied.overwritten"><strong>{{ applied.overwritten }}</strong> перезаписано поверх существующих</span>
        <span><strong>{{ applied.skippedOccupied }}</strong> занятых слотов пропущено</span>
        <span><strong>{{ applied.pendingQuestions }}</strong> вопросов по неизвестным</span>
      </div>
    </section>

    <section v-if="questions.length" class="card">
      <h2>Неизвестные активности</h2>
      <div v-for="q in questions" :key="q.id" class="question-row">
        <span>{{ q.activityText }}</span><small>{{ q.startAt }}</small>
        <button class="btn btn-ghost" @click="resolve(q)">Создать Дело и продолжить</button>
      </div>
    </section>
    <p v-if="resolved" class="banner ok">Активность «{{ resolved }}» добавлена в словарь.</p>
  </div>
</template>

<style scoped>
.import-card { display: grid; gap: 1rem; max-width: 48rem; }
.card { max-width: 48rem; }
.file-picker { display: grid; gap: .4rem; color: var(--muted-foreground, #756d64); }
.import-progress, .question-row { display: flex; flex-wrap: wrap; gap: .8rem; align-items: center; }
.question-row { justify-content: space-between; border-top: 1px solid var(--border, #e6dfd4); padding: .7rem 0; }
.question-row small { color: var(--muted-foreground, #756d64); }
.summary { display: flex; flex-wrap: wrap; gap: 1.2rem; margin: .6rem 0 1rem; }
.summary span { color: var(--muted-foreground, #756d64); }
.summary strong { color: var(--foreground, #1f2328); }
.activities table { width: 100%; border-collapse: collapse; margin-top: .6rem; }
.activities th, .activities td { text-align: left; padding: .35rem .5rem; border-bottom: 1px solid var(--border, #e6dfd4); }
.activities td.num { font-variant-numeric: tabular-nums; }
.hint { color: var(--muted-foreground, #756d64); font-size: .85rem; margin: 0; }
.banner.warn { background: #fdf3e3; border: 1px solid #e6cf9f; color: #7a5a1a; padding: .5rem .7rem; border-radius: 6px; }
</style>
