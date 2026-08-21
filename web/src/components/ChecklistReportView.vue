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
    <template v-if="report">
      <p class="report-totals">Отмечено {{ report.checkedTotal }} из {{ report.itemsTotal }} · переключений: {{ report.distractionsTotal }}</p>
      <div class="report-days">
        <section v-for="day in report.days" :key="day.date" class="report-day">
          <header class="report-day-head">
            <h2>{{ day.date }}</h2>
            <span class="report-day-meta">Отмечено {{ day.checkedCount }} из {{ day.totalCount }}</span>
          </header>
          <ul v-if="day.items.length" class="report-items">
            <li v-for="item in day.items" :key="item.title" :class="{ done: item.done }">
              <span class="mark" aria-hidden="true">{{ item.done ? '✓' : '·' }}</span>
              <span class="item-title">{{ item.title }}</span>
              <small v-if="item.deloTitle" class="item-delo">{{ item.deloTitle }}</small>
            </li>
          </ul>
          <p v-else class="report-day-empty">Записей за этот день нет.</p>
          <div v-for="switchItem in day.distractions" :key="switchItem.at" class="switch-row">
            <span class="switch-time">{{ String(switchItem.at).slice(11, 16) }}</span>
            переключение на {{ switchItem.target || 'текст' }}
            <small v-if="switchItem.minutes"> · {{ switchItem.minutes }} мин</small>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.checklist-report { display: grid; gap: 1rem; }
.report-toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: .75rem; }
.report-toolbar label { display: grid; gap: .25rem; color: var(--wolf-muted); font-size: .8rem; }
.report-totals { margin: 0; color: var(--wolf-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.report-days { display: grid; gap: 0; border-top: 1px solid var(--wolf-rule); }

/* Chronological ruled table grouped by day; a day without data stays neutral. */
.report-day { padding: 12px 0 14px; border-bottom: 1px solid var(--wolf-rule); }
.report-day-head { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
.report-day h2 { margin: 0; font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.report-day-meta { color: var(--wolf-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.report-day-empty { margin: 8px 0 0; color: var(--wolf-muted); font-size: 12px; }

.report-items { list-style: none; margin: .5rem 0 0; padding: 0; display: grid; gap: 0; }
.report-items li {
  display: grid;
  grid-template-columns: 1.2rem minmax(0, 1fr) auto;
  gap: .5rem;
  align-items: baseline;
  padding: 5px 6px;
  color: var(--wolf-ink);
  font-size: 13px;
  border-bottom: 1px solid var(--wolf-subrule);
}
/* Pale green marks completion only — never a score, never a missing-day signal. */
.report-items li.done { background: var(--wolf-done-surface); }
.report-items li.done .mark { color: var(--wolf-done-ink); }
.mark { color: var(--wolf-muted); font-variant-numeric: tabular-nums; }
.item-title { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-delo { color: var(--wolf-muted); font-size: 11px; }

.switch-row { color: var(--wolf-muted); font-size: 12px; padding-top: .4rem; margin-top: .4rem; }
.switch-time { font-variant-numeric: tabular-nums; margin-right: .35rem; }
</style>
