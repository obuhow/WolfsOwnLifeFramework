<script setup>
import { computed, onMounted, ref } from 'vue'
import GanttView from './GanttView.vue'
import { apiBase, authHeaders } from '../api'

const capacity = ref([])
const capacityError = ref('')
const curve = ref([])

async function loadCapacity() {
  const headers = authHeaders()
  if (!headers) return
  const now = new Date()
  const from = new Date(now); from.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  const to = new Date(from); to.setDate(from.getDate() + 16 * 7 - 1)
  const iso = d => d.toISOString().slice(0, 10)
  try {
    const res = await fetch(`${apiBase()}/planning/capacity?from=${iso(from)}&to=${iso(to)}`, { headers })
    if (!res.ok) throw new Error(`Капасити: HTTP ${res.status}`)
    capacity.value = await res.json()
    const projects = await fetch(`${apiBase()}/projects`, { headers })
    if (projects.ok) {
      const list = await projects.json()
      const curves = await Promise.all(list.map(p => fetch(`${apiBase()}/projects/${p.id}/load-curve`, { headers }).then(r => r.ok ? r.json() : [])))
      curve.value = curves.flatMap((items, index) => items.map(item => ({ ...item, title: list[index].title })))
    }
  } catch (e) { capacityError.value = e.message }
}

function formatHours(v) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return Number.isInteger(n) ? String(n) : n.toFixed(1).replace(/\.0$/, '')
}

/** Neutral textual delta — context, never a score or a blocking warning. */
function deltaLabel(item) {
  const d = Number(item.delta)
  if (Number.isNaN(d)) return ''
  if (d === 0) return 'план равен доступным часам'
  if (d > 0) return `свободно ${formatHours(d)} ч`
  return `план больше доступного на ${formatHours(Math.abs(d))} ч`
}

const hasCapacity = computed(() => capacity.value.length > 0)

onMounted(loadCapacity)
</script>

<template>
  <div class="planning-page">
    <GanttView />

    <section class="planning-capacity">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Нагрузка</span>
          <h2>Суммарная плановая нагрузка</h2>
        </div>
      </div>
      <p v-if="capacityError" class="banner error">{{ capacityError }}</p>
      <div v-else-if="!hasCapacity" class="muted">Нет данных по неделям</div>
      <div v-else class="capacity-scroll" role="region" aria-label="Нагрузка по неделям, прокрутка по горизонтали" tabindex="0">
        <div class="capacity-list">
          <div class="capacity-row capacity-head">
            <span>Неделя</span>
            <span>План</span>
            <span>Доступно</span>
            <span>Разница</span>
          </div>
          <div v-for="item in capacity" :key="item.weekId" class="capacity-row">
            <strong class="cap-week">{{ item.weekId }}</strong>
            <span class="cap-num">{{ formatHours(item.plannedHours) }} ч</span>
            <span class="cap-num">{{ formatHours(item.availableHours) }} ч</span>
            <span class="cap-delta">{{ deltaLabel(item) }}</span>
          </div>
        </div>
      </div>

      <div v-if="curve.length" class="curve-list">
        <h3>Кривые нагрузки</h3>
        <div v-for="item in curve" :key="item.id" class="curve-row">
          <strong>{{ item.title }}</strong>
          <span class="cap-num">{{ item.weekStart }}</span>
          <span class="cap-num">{{ formatHours(item.hours) }} ч/нед</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.planning-page { width: 100%; }

.planning-capacity {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--wolf-rule);
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

.section-heading { margin-bottom: 0.85rem; }
.section-heading h2 { margin: 0; font-size: 1rem; }
.section-heading .eyebrow {
  display: block;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--wolf-muted);
}

.capacity-scroll { overflow-x: auto; }
.capacity-list { display: grid; min-width: 34rem; }

.capacity-row {
  display: grid;
  grid-template-columns: 7rem 6rem 7rem minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
  padding: 0.45rem 0;
  border-top: 1px solid var(--wolf-subrule);
  font-size: 0.85rem;
  color: var(--wolf-ink);
}

.capacity-row.capacity-head {
  border-top: 0;
  border-bottom: 1px solid var(--wolf-rule);
  color: var(--wolf-muted);
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.cap-week { font-variant-numeric: tabular-nums; font-weight: 600; }
.cap-num { font-variant-numeric: tabular-nums; }
.cap-delta { color: var(--wolf-muted); }

.curve-list {
  margin-top: 1.5rem;
  border-top: 1px solid var(--wolf-rule);
  padding-top: 0.9rem;
}
.curve-list h3 { margin: 0 0 0.5rem; font-size: 0.9rem; }
.curve-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 8rem 8rem;
  gap: 0.75rem;
  padding: 0.4rem 0;
  border-top: 1px solid var(--wolf-subrule);
  font-size: 0.85rem;
  color: var(--wolf-ink);
}

.banner { padding: 0.6rem 0; margin: 0 0 0.75rem; border-radius: 0; background: transparent; font-size: 0.9rem; }
.banner.error { color: var(--wolf-ink); border-bottom: 1px solid var(--wolf-ink); }
.muted { color: var(--wolf-muted); }

@media (max-width: 700px) {
  .curve-row { grid-template-columns: minmax(0, 1fr) 6rem; }
}
</style>
