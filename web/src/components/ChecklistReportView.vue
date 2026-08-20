<script setup>
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

const today = new Date()
const to = ref(today.toISOString().slice(0, 10))
const from = ref(new Date(today.getTime() - 13 * 86400000).toISOString().slice(0, 10))
const report = ref(null)
const loading = ref(false)
const error = ref('')

const headers = () => authHeaders()
async function load() {
  loading.value = true; error.value = ''
  try {
    const res = await fetch(`${apiBase()}/reports/checklist?from=${from.value}&to=${to.value}`, { headers: headers() })
    if (handleAuthFailure(res)) return
    if (!res.ok) throw new Error(`Отчёт: HTTP ${res.status}`)
    report.value = await res.json()
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) } finally { loading.value = false }
}
function setDays(days) {
  const end = new Date(`${to.value}T00:00:00`); end.setDate(end.getDate() - days + 1); from.value = end.toISOString().slice(0, 10); load()
}
async function download(format) {
  const res = await fetch(`${apiBase()}/reports/checklist/export?format=${format}&from=${from.value}&to=${to.value}`, { headers: headers() })
  if (!res.ok) return
  const blob = await res.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `checklist-report.${format}`; a.click(); URL.revokeObjectURL(url)
}
async function saveAsNote() {
  if (!report.value) return
  const parent = report.value.days.flatMap(d => d.items).find(i => i.deloId)
  if (!parent) { error.value = 'Для заметки нужен пункт, связанный с Делом'; return }
  const exportRes = await fetch(`${apiBase()}/reports/checklist/export?format=md&from=${from.value}&to=${to.value}`, { headers: headers() })
  const body = await exportRes.text()
  const res = await fetch(`${apiBase()}/notes`, { method: 'POST', headers: { ...headers(), 'Content-Type': 'application/json' }, body: JSON.stringify({ deloId: parent.deloId, body, tags: ['checklist-report'] }) })
  if (!res.ok) throw new Error(`Заметка: HTTP ${res.status}`)
  error.value = ''
}
onMounted(load)
</script>

<template>
  <section class="page checklist-report">
    <header class="page-header"><div><p class="eyebrow">Зеркало факта</p><h1>Отчёт — чек-лист</h1><p class="page-intro">Пункты дня и отметки переключений без оценок и процентов.</p></div></header>
    <div class="report-toolbar card"><label>От <input v-model="from" type="date" @change="load" /></label><label>До <input v-model="to" type="date" @change="load" /></label><button class="btn btn-ghost" @click="setDays(7)">7 дней</button><button class="btn btn-ghost" @click="setDays(14)">14 дней</button><button class="btn btn-ghost" @click="setDays(30)">30 дней</button><button class="btn btn-ghost" @click="download('md')">Markdown</button><button class="btn btn-ghost" @click="download('csv')">CSV</button><button class="btn btn-primary" @click="saveAsNote">Сохранить как Заметку</button></div>
    <p v-if="error" class="alert alert-error">{{ error }}</p><p v-if="loading" class="loading">Загрузка…</p>
    <template v-if="report"><div class="report-totals card"><strong>Отмечено {{ report.checkedTotal }} из {{ report.itemsTotal }}</strong><span>Переключений: {{ report.distractionsTotal }}</span></div><div class="report-days"><article v-for="day in report.days" :key="day.date" class="card report-day"><h2>{{ day.date }}</h2><p class="hint">Отмечено {{ day.checkedCount }} из {{ day.totalCount }}</p><ul v-if="day.items.length" class="report-items"><li v-for="item in day.items" :key="item.title"><span :class="{ checked: item.done }">{{ item.done ? '✓' : '·' }}</span>{{ item.title }}<small v-if="item.deloTitle"> · {{ item.deloTitle }}</small></li></ul><p v-else class="hint">Пусто</p><div v-for="switchItem in day.distractions" :key="switchItem.at" class="switch-row">переключение на {{ switchItem.target || 'текст' }}<small v-if="switchItem.minutes"> · {{ switchItem.minutes }} мин</small></div></article></div></template>
  </section>
</template>

<style scoped>
.checklist-report { display: grid; gap: 1rem; }
.report-toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: .75rem; }
.report-toolbar label { display: grid; gap: .25rem; color: var(--wolf-muted); font-size: .8rem; }
.report-totals { display: flex; gap: 1.25rem; }
.report-days { display: grid; gap: .75rem; }
.report-day h2 { margin: 0; }
.report-items { list-style: none; margin: .5rem 0; padding: 0; display: grid; gap: .35rem; }
.report-items li { color: var(--wolf-ink); }
.report-items .checked { color: var(--wolf-muted); }
.report-items small { color: var(--wolf-muted); }
.switch-row { color: var(--wolf-muted); border-top: 1px solid var(--wolf-rule); padding-top: .4rem; margin-top: .4rem; }
</style>
