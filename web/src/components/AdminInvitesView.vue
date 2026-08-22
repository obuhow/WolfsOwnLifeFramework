<script setup>
/**
 * Настройки → Пользователи/Инвайт-коды (release 0.4, ticket 11).
 * Visible only to role === 'ADMIN'. Registry of issued invite codes + issue form.
 * Ruled reference list, no modals, no red fills — contract 0.3.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiBase, authHeaders } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const router = useRouter()
const authorized = ref(false)
const checkingRole = ref(true)

const codes = ref([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')

const form = ref({ maxUses: 1, expiresAt: '', note: '' })
const showForm = ref(false)
const lastIssuedCode = ref('')
const copyHint = ref('')

const activeCodes = computed(() => codes.value.filter(c => !c.revokedAt))
const revokedCodes = computed(() => codes.value.filter(c => c.revokedAt))

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/admin/invite-codes`, { headers })
    if (!res.ok) throw new Error(`Инвайт-коды: HTTP ${res.status}`)
    // Server already returns findAllByOrderByCreatedAtDesc — do not re-sort by
    // client-side createdAt: for a freshly issued code it comes back null.
    codes.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function startCreate() {
  form.value = { maxUses: 1, expiresAt: '', note: '' }
  lastIssuedCode.value = ''
  copyHint.value = ''
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
}

async function issue() {
  const headers = authHeaders(true)
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const body = {
      maxUses: form.value.maxUses || 1,
      expiresAt: form.value.expiresAt ? new Date(form.value.expiresAt).toISOString() : null,
      note: form.value.note.trim() || null
    }
    const res = await fetch(`${apiBase()}/admin/invite-codes`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    })
    if (!res.ok) {
      const text = await res.text()
      let message = `Выпуск кода: HTTP ${res.status}`
      try { message = JSON.parse(text).message || message } catch { if (text) message = text }
      throw new Error(message)
    }
    const created = await res.json()
    lastIssuedCode.value = created.code
    showForm.value = false
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function revoke(code) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/admin/invite-codes/${code.id}/revoke`, { method: 'POST', headers })
    if (!res.ok) throw new Error(`Отзыв кода: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function copyCode(value) {
  try {
    await navigator.clipboard.writeText(value)
    copyHint.value = 'Скопировано'
  } catch {
    copyHint.value = value
  }
  setTimeout(() => { copyHint.value = '' }, 2500)
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

async function checkRole() {
  const headers = authHeaders()
  if (!headers) return
  try {
    const res = await fetch(`${apiBase()}/auth/me`, { headers })
    if (!res.ok) {
      router.replace('/today')
      return
    }
    const me = await res.json()
    if (me.role !== 'ADMIN') {
      router.replace('/today')
      return
    }
    authorized.value = true
    await load()
  } catch {
    router.replace('/today')
  } finally {
    checkingRole.value = false
  }
}

onMounted(checkRole)
</script>

<template>
  <div v-if="authorized" class="invites-page">
    <header class="page-header invites-header">
      <div>
        <h1>Инвайт-коды</h1>
        <p class="eyebrow">Настройки · выпуск и отзыв пригласительных кодов</p>
      </div>
      <button v-if="!showForm" type="button" class="btn btn-primary" :disabled="busy" @click="startCreate">Выпустить код</button>
    </header>

    <p v-if="error" class="alert alert-error">{{ error }}</p>

    <p v-if="lastIssuedCode" class="alert alert-success issued-code-banner">
      Код выпущен: <code class="issued-code">{{ lastIssuedCode }}</code>
      <button type="button" class="btn btn-ghost btn-sm" @click="copyCode(lastIssuedCode)">Скопировать</button>
      <span v-if="copyHint" class="copy-hint">{{ copyHint }}</span>
    </p>

    <form v-if="showForm" class="invite-form" @submit.prevent="issue">
      <label>
        <span>Лимит использований</span>
        <input v-model.number="form.maxUses" class="input" type="number" min="1" step="1" required />
      </label>
      <label>
        <span>Срок действия (необязательно)</span>
        <input v-model="form.expiresAt" class="input" type="datetime-local" />
      </label>
      <label>
        <span>Примечание (необязательно)</span>
        <input v-model="form.note" class="input" maxlength="255" placeholder="Кому предназначен код" />
      </label>
      <div class="form-actions">
        <button type="button" class="btn btn-ghost" :disabled="busy" @click="cancelForm">Отмена</button>
        <button type="submit" class="btn btn-primary" :disabled="busy || !form.maxUses">Выпустить</button>
      </div>
    </form>

    <div v-if="loading" class="loading">Загрузка…</div>

    <template v-else>
      <ul v-if="activeCodes.length" class="invite-list">
        <li v-for="c in activeCodes" :key="c.id" class="invite-row">
          <div class="invite-body">
            <div class="invite-code-line">
              <code class="invite-code">{{ c.code }}</code>
              <button type="button" class="btn btn-ghost btn-sm" @click="copyCode(c.code)">Копировать</button>
            </div>
            <span class="invite-meta">
              Выпустил {{ c.createdByUsername }} · {{ c.usedCount }}/{{ c.maxUses }} использовано
              <template v-if="c.expiresAt"> · до {{ formatDate(c.expiresAt) }}</template>
              <template v-if="c.note"> · {{ c.note }}</template>
            </span>
          </div>
          <div class="invite-row-actions">
            <ConfirmInline
              label="Отозвать"
              :question="`Отозвать код «${c.code}»?`"
              confirm-label="Да, отозвать"
              :disabled="busy"
              @confirm="revoke(c)"
            />
          </div>
        </li>
      </ul>
      <p v-else class="hint">Активных инвайт-кодов пока нет.</p>

      <template v-if="revokedCodes.length">
        <h2 class="invites-subheading">Отозванные</h2>
        <ul class="invite-list">
          <li v-for="c in revokedCodes" :key="c.id" class="invite-row revoked">
            <div class="invite-body">
              <code class="invite-code">{{ c.code }}</code>
              <span class="invite-meta">
                Выпустил {{ c.createdByUsername }} · {{ c.usedCount }}/{{ c.maxUses }} использовано · отозван {{ formatDate(c.revokedAt) }}
              </span>
            </div>
          </li>
        </ul>
      </template>
    </template>
  </div>
</template>

<style scoped>
.invites-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.issued-code-banner { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.issued-code, .invite-code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; letter-spacing: 0.03em; }
.copy-hint { color: var(--wolf-muted); font-size: 11px; }

.invite-form {
  display: grid;
  gap: 12px;
  padding: 14px 0 16px;
  border-top: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 16px;
}

.invite-form label { display: grid; gap: 4px; color: var(--wolf-muted); font-size: 12px; }
.form-actions { display: flex; justify-content: flex-end; gap: 1rem; }

.invites-subheading { font-size: 13px; font-weight: 600; margin: 24px 0 0; color: var(--wolf-ink); }

.invite-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--wolf-rule); }

.invite-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--wolf-rule);
}

.invite-row.revoked .invite-code { color: var(--wolf-muted); }
.invite-body { display: grid; gap: 3px; min-width: 0; }
.invite-code-line { display: flex; align-items: center; gap: 0.5rem; }
.invite-meta { color: var(--wolf-muted); font-size: 12px; line-height: 1.45; }
.invite-row-actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }

@media (max-width: 640px) {
  .invite-row { grid-template-columns: 1fr; }
  .invite-row-actions { justify-content: flex-start; }
}
</style>
