<script setup>
import { onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'

const delos = ref([])
const selectedDeloId = ref('')
const session = ref(null)
const distractionText = ref('')
const loading = ref(false)
const error = ref('')
const notice = ref('')
const review = ref(null)
const reviewAllocations = ref([])

async function load() {
  const headers = authHeaders()
  if (!headers) return
  try {
    const [deloResponse, currentResponse] = await Promise.all([
      fetch(`${apiBase()}/delos`, { headers }),
      fetch(`${apiBase()}/focus/current`, { headers })
    ])
    if (!deloResponse.ok || !currentResponse.ok) throw new Error('Фокус: не удалось загрузить данные')
    delos.value = await deloResponse.json()
    session.value = await currentResponse.json()
    if (!selectedDeloId.value && delos.value.length) selectedDeloId.value = delos.value[0].id
  } catch (e) {
    error.value = e.message
  }
}

async function start() {
  if (!selectedDeloId.value) return
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const response = await fetch(`${apiBase()}/focus/start`, {
      method: 'POST', headers: authHeaders(true),
      body: JSON.stringify({ deloId: Number(selectedDeloId.value) })
    })
    if (!response.ok) throw new Error(`Старт фокус-сессии: HTTP ${response.status}`)
    session.value = await response.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function stop() {
  if (!session.value) return
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const response = await fetch(`${apiBase()}/focus/${session.value.id}/stop`, {
      method: 'POST', headers: authHeaders(true), body: JSON.stringify({})
    })
    if (!response.ok) throw new Error(`Остановка фокус-сессии: HTTP ${response.status}`)
    const result = await response.json()
    session.value = result.session || result
    notice.value = 'Сессия сохранена. Свободные 15-минутные ячейки добавлены в факт.'
    await openReview(session.value.id)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function openReview(id) {
  const response = await fetch(`${apiBase()}/focus/${id}/review`, { headers: authHeaders() })
  if (!response.ok) return
  review.value = await response.json()
  reviewAllocations.value = review.value.distractions.map(item => ({ distractionId: item.id, deloId: item.deloId || '', minutes: item.suggestedMinutes || '' }))
}

async function applyReview() {
  if (!review.value) return
  const response = await fetch(`${apiBase()}/focus/${review.value.sessionId}/review/apply`, { method: 'POST', headers: authHeaders(true), body: JSON.stringify({ allocations: reviewAllocations.value.filter(item => item.minutes).map(item => ({ ...item, minutes: Number(item.minutes), deloId: Number(item.deloId) })) }) })
  if (!response.ok) { error.value = `Разбор: HTTP ${response.status}`; return }
  review.value = null; notice.value = 'Разбор применён. Часы переразнесены по выбранным Делам.'
}

function keepAsIs() { review.value = null; notice.value = 'Переключения оставлены как отметки; часы не переразносились.' }

async function addDistraction() {
  const text = distractionText.value.trim()
  if (!session.value || !text) return
  const response = await fetch(`${apiBase()}/focus/${session.value.id}/distractions`, {
    method: 'POST', headers: authHeaders(true), body: JSON.stringify({ text })
  })
  if (!response.ok) { error.value = `Отметка переключения: HTTP ${response.status}`; return }
  distractionText.value = ''
  notice.value = 'Отметка переключения добавлена; сетка не изменена.'
}

onMounted(load)
</script>

<template>
  <section class="card focus-card" aria-label="Фокус-сессия">
    <div class="focus-header">
      <div>
        <h2>Главное дело</h2>
        <p class="hint">Фокус-сессия фиксирует факт, а переключения остаются отметками чек-листа.</p>
      </div>
      <span v-if="session && !session.endedAt" class="focus-status">идёт</span>
    </div>
    <div v-if="!session || session.endedAt" class="focus-start">
      <select v-model="selectedDeloId" class="input" :disabled="loading">
        <option value="" disabled>Выберите Дело</option>
        <option v-for="delo in delos" :key="delo.id" :value="delo.id">{{ delo.title }}</option>
      </select>
      <button class="btn btn-primary" :disabled="loading || !selectedDeloId" @click="start">Начать фокус</button>
    </div>
    <div v-else class="focus-running">
      <strong>{{ delos.find(d => d.id === session.deloId)?.title || `Дело #${session.deloId}` }}</strong>
      <span class="hint">с {{ session.startedAt.replace('T', ' ').slice(0, 16) }}</span>
      <div class="focus-actions">
        <input v-model="distractionText" class="input" placeholder="Что проверил или на что переключился" @keyup.enter="addDistraction" />
        <button class="btn btn-ghost" :disabled="loading || !distractionText.trim()" @click="addDistraction">Переключился</button>
        <button class="btn btn-primary" :disabled="loading" @click="stop">Завершить</button>
      </div>
    </div>
    <p v-if="notice" class="hint focus-notice">{{ notice }}</p>
    <p v-if="error" class="alert alert-error">{{ error }}</p>
    <div v-if="review" class="review-box" aria-label="Разбор переключений">
      <h3>Разбор переключений</h3><p class="hint">Можно указать минуты или оставить отметку как есть.</p>
      <div v-for="(item, index) in review.distractions" :key="item.id" class="review-row">
        <span>переключение на {{ item.deloTitle || item.text || 'текст' }}</span>
        <input v-model="reviewAllocations[index].minutes" class="input input-sm" type="number" min="15" step="15" placeholder="минуты" />
        <select v-if="item.deloId === null" v-model="reviewAllocations[index].deloId" class="input input-sm"><option value="">Дело</option><option v-for="delo in delos" :key="delo.id" :value="delo.id">{{ delo.title }}</option></select>
      </div>
      <div class="review-actions"><button class="btn btn-primary" @click="applyReview">Применить</button><button class="btn btn-ghost" @click="keepAsIs">Оставить как есть</button></div>
    </div>
  </section>
</template>

<style scoped>
.focus-card { margin-bottom: 1rem; }
.focus-header, .focus-start, .focus-running, .focus-actions { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
.focus-header { justify-content: space-between; }
.focus-header h2 { margin: 0; }
.focus-status { color: #4f6d8a; font-size: .8rem; }
.focus-running { justify-content: space-between; }
.focus-actions { flex: 1 1 28rem; justify-content: flex-end; }
.focus-actions .input { min-width: 16rem; flex: 1; }
.focus-notice { margin-bottom: 0; }
.review-box { margin-top: 1rem; border-top: 1px solid var(--wolf-rule); padding-top: 1rem; }
.review-row { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; padding: .35rem 0; }
.review-row > span { flex: 1; }
.review-row .input { width: 7rem; }
.review-actions { display: flex; gap: .5rem; margin-top: .75rem; }
</style>
