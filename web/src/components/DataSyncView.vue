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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { apiBase } from '../api'

const file = ref(null)
const preview = ref(null)
const result = ref(null)
const loading = ref(false)
const error = ref('')
const deleteMissing = ref(false)
const scopes = ref([])
const manifest = ref(null)
const now = ref(Date.now())

// Тикающие часы: preview протухает по expiresAt, кнопка Apply должна
// погаснуть сама, без перезагрузки страницы (чек-лист 0.21-06).
let clockId = null
onMounted(() => { clockId = setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { if (clockId) clearInterval(clockId) })

const token = () => localStorage.getItem('wolf_token')
const headers = () => ({ Authorization: `Bearer ${token()}` })

const expired = computed(() => {
  const exp = preview.value?.expiresAt
  if (!exp) return false
  return new Date(exp).getTime() <= now.value
})
const canApply = computed(() => preview.value?.applyAllowed === true && !expired.value && !loading.value)

function selectFile(event) { file.value = event.target.files?.[0] || null; preview.value = null; result.value = null; error.value = '' }

// created/updated/deleted приходят как Map<String,Integer>; без форматирования
// интерполяция Vue дала бы [object Object].
function fmtCounts(map) {
  if (!map) return '—'
  const parts = Object.entries(map).filter(([, n]) => n > 0).map(([k, n]) => `${k}: ${n}`)
  return parts.length ? parts.join(', ') : '—'
}

async function loadManifest() {
  try {
    const response = await fetch(`${apiBase()}/data-sync/manifest?version=0.21`, { headers: headers() })
    if (!response.ok) return
    manifest.value = await response.json()
  } catch { /* manifest справочный, его отсутствие не ломает импорт */ }
}
onMounted(loadManifest)

async function downloadExport() {
  loading.value = true; error.value = ''
  try {
    const response = await fetch(`${apiBase()}/data-sync/export?format=xlsx&version=0.21`, { headers: headers() })
    if (!response.ok) throw new Error(`Экспорт: HTTP ${response.status} ${await response.text()}`)
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a')
    link.href = url; link.download = 'wolf-data-0.21.xlsx'; link.click(); URL.revokeObjectURL(url)
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) } finally { loading.value = false }
}

// CSV-экспорт (релиз 1.0, тикет 09): тот же контракт data-sync, что xlsx, но один
// текстовый CSV со всеми листами — пригоден для обратного импорта (раунд-трип).
async function downloadExportCsv() {
  loading.value = true; error.value = ''
  try {
    const response = await fetch(`${apiBase()}/data-sync/export?format=csv&version=0.21`, { headers: headers() })
    if (!response.ok) throw new Error(`Экспорт CSV: HTTP ${response.status} ${await response.text()}`)
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a')
    link.href = url; link.download = 'wolf-data-0.21.csv'; link.click(); URL.revokeObjectURL(url)
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) } finally { loading.value = false }
}

async function makePreview() {
  if (!file.value) return
  loading.value = true; error.value = ''; result.value = null
  try {
    const body = new FormData(); body.append('file', file.value)
    const response = await fetch(`${apiBase()}/data-sync/import/preview`, { method: 'POST', headers: headers(), body })
    const text = await response.text(); let data = {}; try { data = JSON.parse(text) } catch { data = { message: text } }
    if (!response.ok) throw new Error(`Preview: HTTP ${response.status} ${data.message || text}`)
    preview.value = data
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) } finally { loading.value = false }
}

async function applyPreview() {
  if (!canApply.value) return
  loading.value = true; error.value = ''
  try {
    const response = await fetch(`${apiBase()}/data-sync/import/${preview.value.id}/apply`, {
      method: 'POST', headers: { ...headers(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ checksum: preview.value.checksum, deleteMissing: deleteMissing.value, scopes: scopes.value })
    })
    const text = await response.text(); let data = {}; try { data = JSON.parse(text) } catch { data = { message: text } }
    if (!response.ok) throw new Error(`Apply: HTTP ${response.status} ${data.message || text}`)
    result.value = data
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) } finally { loading.value = false }
}
</script>

<template>
  <div class="settings-page data-sync-page">
    <header class="page-header">
      <h1>Синхронизация данных</h1>
      <p class="eyebrow">Полный workbook WOLF 0.21: экспорт, preview и явное применение</p>
    </header>

    <section class="card sync-panel">
      <div class="sync-actions">
        <button class="btn btn-primary" :disabled="loading" @click="downloadExport">Скачать XLSX</button>
        <button class="btn" :disabled="loading" @click="downloadExportCsv">Экспорт в CSV</button>
        <label class="file-control">Выбрать файл
          <input data-testid="sync-file" type="file" accept=".xlsx,.csv" @change="selectFile" />
        </label>
        <button class="btn" :disabled="!file || loading" @click="makePreview">Показать preview</button>
      </div>
      <p class="hint">Канонический формат: manifest + 17 листов, externalId для связей. XLSX или CSV — один и тот же контракт (раунд-трип). Загрузка не изменяет данные автоматически.</p>
      <p v-if="manifest" class="hint" data-testid="sync-manifest">
        Контракт: {{ manifest.format }} версия {{ manifest.version }} · листов: {{ manifest.sheets?.length ?? 0 }}
      </p>

      <div v-if="preview" class="preview" aria-live="polite">
        <h2>Preview #{{ preview.id }} — {{ preview.status }}</h2>
        <p>Checksum: <code>{{ preview.checksum }}</code></p>
        <p v-if="preview.expiresAt" class="hint" data-testid="sync-expiry">
          <template v-if="expired">Preview устарел — загрузите файл заново, применение недоступно.</template>
          <template v-else>Действителен до {{ new Date(preview.expiresAt).toLocaleString('ru-RU') }}.</template>
        </p>
        <div class="summary-grid">
          <div v-for="(count, sheet) in preview.counts" :key="sheet"><strong>{{ sheet }}</strong><span>{{ count }} строк</span></div>
        </div>
        <div v-if="preview.plan" class="summary-grid" data-testid="sync-plan">
          <div v-for="(change, sheet) in preview.plan" :key="`plan-${sheet}`"><strong>{{ sheet }}</strong><span>+{{ change.create }} / ↻{{ change.update }} / ={{ change.skip }} / −{{ change.delete }}</span></div>
        </div>
        <div v-if="preview.errors?.length" class="alert alert-error">
          <strong>Ошибки: {{ preview.errors.length }}</strong>
          <table><thead><tr><th>Лист</th><th>Строка</th><th>Поле</th><th>Сообщение</th></tr></thead>
            <tbody><tr v-for="item in preview.errors" :key="`${item.sheet}-${item.row}-${item.field}`"><td>{{ item.sheet }}</td><td>{{ item.row }}</td><td>{{ item.field }}</td><td>{{ item.message }}</td></tr></tbody>
          </table>
        </div>
        <fieldset class="delete-options">
          <legend>Удаление отсутствующих данных</legend>
          <label><input v-model="deleteMissing" type="checkbox" /> Включить только явно выбранные scopes</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="projects" type="checkbox" /> projects</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="delos" type="checkbox" /> delos</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="routine_schedules" type="checkbox" /> routine_schedules</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="goal_metrics" type="checkbox" /> goal_metrics</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="goal_week_budgets" type="checkbox" /> goal_week_budgets</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="synergies" type="checkbox" /> synergies</label>
          <label v-if="deleteMissing"><input v-model="scopes" value="project_dependencies" type="checkbox" /> project_dependencies</label>
        </fieldset>
        <button class="btn btn-primary" :disabled="!canApply" @click="applyPreview">Применить preview атомарно</button>
      </div>

      <div v-if="result" class="alert alert-success" data-testid="sync-result">
        Применено: {{ result.status }}; создано: {{ fmtCounts(result.created) }}; обновлено: {{ fmtCounts(result.updated) }}; удалено: {{ fmtCounts(result.deleted) }}
      </div>
      <div v-if="error" class="alert alert-error" data-testid="sync-error">{{ error }}</div>
    </section>
  </div>
</template>

<style scoped>
.data-sync-page { max-width: 960px; }
.sync-panel { display: grid; gap: 18px; }
.sync-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.file-control { border-bottom: 1px solid var(--wolf-ink); padding: 8px 0; cursor: pointer; }
.file-control input { margin-left: 8px; max-width: 220px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; }
.summary-grid div { display: flex; justify-content: space-between; border-bottom: 1px solid var(--wolf-rule); padding: 6px 0; }
.preview { display: grid; gap: 14px; border-top: 1px solid var(--wolf-rule); padding-top: 18px; }
.preview h2 { margin: 0; font-size: 18px; }
.delete-options { display: grid; gap: 8px; border: 1px solid var(--wolf-rule); padding: 12px; }
table { width: 100%; border-collapse: collapse; margin-top: 10px; } th, td { text-align: left; border-bottom: 1px solid var(--wolf-rule); padding: 6px; }
@media (max-width: 600px) { .sync-actions { align-items: stretch; flex-direction: column; } .file-control input { display: block; margin: 8px 0 0; } }
</style>

<style>
.data-sync-page .btn { cursor: pointer; }
.data-sync-page .btn:disabled { opacity: .45; cursor: not-allowed; }
</style>
