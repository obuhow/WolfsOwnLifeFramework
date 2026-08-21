<script setup>
import { onMounted, ref } from 'vue'
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

onMounted(loadCapacity)
</script>

<template>
  <div>
    <GanttView />
    <section class="card planning-capacity">
      <div class="section-heading"><div><span class="eyebrow">Нагрузка</span><h2>Суммарная плановая нагрузка</h2></div></div>
      <p v-if="capacityError" class="banner error">{{ capacityError }}</p>
      <div v-else-if="!capacity.length" class="muted">Нет данных по неделям</div>
      <div v-else class="capacity-list">
        <div v-for="item in capacity" :key="item.weekId" class="capacity-row">
          <strong>{{ item.weekId }}</strong><span>план {{ item.plannedHours }} ч</span><span>доступно {{ item.availableHours }} ч</span>
          <span class="muted">дельта {{ item.delta }} ч</span>
          <small v-if="Number(item.delta) < 0" class="muted">план недели превышает доступные часы на {{ Math.abs(Number(item.delta)) }} ч</small>
        </div>
      </div>
      <div v-if="curve.length" class="curve-list">
        <h3>Кривые нагрузки</h3>
        <div v-for="item in curve" :key="item.id" class="curve-row"><strong>{{ item.title }}</strong><span>{{ item.weekStart }}</span><span>{{ item.hours }} ч/нед</span></div>
      </div>
    </section>
  </div>
</template>


<style scoped>
.planning-capacity { margin-top: 1.25rem; }
.capacity-list { display: grid; gap: .35rem; }
.capacity-row { display: grid; grid-template-columns: 7rem 9rem 10rem 8rem 1fr; gap: .75rem; align-items: center; padding: .55rem .7rem; border-top: 1px solid var(--border, #e6dfd4); }
.capacity-row small { color: var(--muted-foreground, #7a7268); }
.curve-list { margin-top: 1.25rem; border-top: 1px solid var(--border, #e6dfd4); padding-top: .8rem; }
.curve-list h3 { margin: 0 0 .5rem; }
.curve-row { display: grid; grid-template-columns: 1fr 8rem 8rem; gap: .75rem; padding: .4rem 0; border-top: 1px solid var(--border, #e6dfd4); }
@media (max-width: 700px) { .capacity-row { grid-template-columns: 1fr 1fr; } }
</style>
