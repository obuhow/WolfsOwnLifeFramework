<script setup>
import { computed, ref } from 'vue'
import { apiBase } from '../api'

const file = ref(null)
const preview = ref(null)
const result = ref(null)
const loading = ref(false)
const error = ref('')
const deleteMissing = ref(false)
const scopes = ref([])

const token = () => localStorage.getItem('wolf_token')
const headers = () => ({ Authorization: `Bearer ${token()}` })
const canApply = computed(() => preview.value?.applyAllowed === true && !loading.value)

function selectFile(event) { file.value = event.target.files?.[0] || null; preview.value = null; result.value = null; error.value = '' }

async function downloadExport() {
  loading.value = true; error.value = ''
  try {
    const response = await fetch(`${apiBase()}/data-sync/export?format=xlsx&version=0.21`, { headers: headers() })
    if (!response.ok) throw new Error(`Экспорт: HTTP ${response.status} ${await response.text()}`)
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a')
    link.href = url; link.download = 'wolf-data-0.21.xlsx'; link.click(); URL.revokeObjectURL(url)
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
        <label class="file-control">Выбрать workbook
          <input data-testid="sync-file" type="file" accept=".xlsx" @change="selectFile" />
        </label>
        <button class="btn" :disabled="!file || loading" @click="makePreview">Показать preview</button>
      </div>
      <p class="hint">Канонический формат: manifest + 17 листов, externalId для связей. Загрузка не изменяет данные автоматически.</p>

      <div v-if="preview" class="preview" aria-live="polite">
        <h2>Preview #{{ preview.id }} — {{ preview.status }}</h2>
        <p>Checksum: <code>{{ preview.checksum }}</code></p>
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
        </fieldset>
        <button class="btn btn-primary" :disabled="!canApply" @click="applyPreview">Применить preview атомарно</button>
      </div>

      <div v-if="result" class="alert alert-success" data-testid="sync-result">Применено: {{ result.status }}; создано: {{ result.created }}; обновлено: {{ result.updated }}</div>
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
