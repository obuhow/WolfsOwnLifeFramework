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
import { computed, nextTick, onMounted, ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'
import {
  actionPresentation,
  actionResultLabel,
  mergeChatResponse,
  newestSession,
} from '../chatPanelModel.js'

const messages = ref([])
const session = ref(null)
const draft = ref('')
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const proposals = ref(new Map())
const messageList = ref(null)

const actionLabels = {
  CREATE_DELO: 'Создать дело',
  UPDATE_DELO: 'Изменить дело',
  DELETE_DELO: 'Удалить дело',
  CREATE_PROJECT: 'Создать проект',
  UPDATE_PROJECT: 'Изменить проект',
  DELETE_PROJECT: 'Удалить проект',
  CREATE_TIME_ENTRY: 'Создать запись времени',
  UPDATE_TIME_ENTRY: 'Изменить запись времени',
  DELETE_TIME_ENTRY: 'Удалить запись времени',
  APPLY_RECURRENCE: 'Применить повторение',
}

const canSend = computed(() => !busy.value && Boolean(draft.value.trim()) && Boolean(session.value))

function proposalFor(messageId) {
  return proposals.value.get(messageId) || null
}

function formatTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function formatFieldValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function readableAction(action) {
  return actionLabels[action?.type] || action?.type || 'Предлагаемое действие'
}

async function apiRequest(path, options = {}) {
  const headers = authHeaders(Boolean(options.body))
  if (!headers) throw new Error('Требуется вход в WOLF')
  const response = await fetch(`${apiBase()}${path}`, { ...options, headers })
  if (handleAuthFailure(response)) {
    throw new Error('Сессия истекла. Войдите снова.')
  }
  if (!response.ok) {
    let message = ''
    try {
      const body = await response.json()
      message = body.message || body.error || ''
    } catch {
      // The status text below is more useful than hiding a non-JSON response.
    }
    throw new Error(message || `Запрос не выполнен: HTTP ${response.status}`)
  }
  return response.status === 204 ? null : response.json()
}

async function loadMessages(sessionId) {
  messages.value = await apiRequest(`/agent-chat/sessions/${sessionId}/messages?page=0&limit=200`)
}

async function loadActions(sessionId) {
  const actions = await apiRequest(`/agent-chat/sessions/${sessionId}/actions`)
  proposals.value = new Map(actions.map((action) => [action.assistantMessageId, action]))
}

async function ensureSession() {
  if (session.value) return session.value
  const sessions = await apiRequest('/agent-chat/sessions')
  session.value = newestSession(sessions)
  if (!session.value) session.value = await apiRequest('/agent-chat/sessions', { method: 'POST' })
  return session.value
}

async function loadChat() {
  loading.value = true
  error.value = ''
  try {
    const current = await ensureSession()
    await loadMessages(current.id)
    await loadActions(current.id)
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
    await nextTick()
    scrollToLatest()
  }
}

async function startNewSession() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    session.value = await apiRequest('/agent-chat/sessions', { method: 'POST' })
    messages.value = []
    proposals.value = new Map()
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

async function send() {
  const content = draft.value.trim()
  if (!content || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const current = await ensureSession()
    const response = await apiRequest(`/agent-chat/sessions/${current.id}/chat`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
    messages.value = mergeChatResponse(messages.value, response)
    if (response.proposedAction) {
      proposals.value = new Map(proposals.value).set(
        response.proposedAction.assistantMessageId,
        response.proposedAction,
      )
    }
    draft.value = ''
    await nextTick()
    scrollToLatest()
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

async function confirmProposal(action) {
  if (!action || !actionPresentation(action).canConfirm || busy.value) return
  await updateProposal(action, 'confirm')
}

async function rejectProposal(action) {
  if (!action || !actionPresentation(action).canConfirm || busy.value) return
  await updateProposal(action, 'reject')
}

async function updateProposal(action, operation) {
  busy.value = true
  error.value = ''
  try {
    const current = await ensureSession()
    const result = await apiRequest(
      `/agent-chat/sessions/${current.id}/actions/${action.id}/${operation}`,
      { method: 'POST' },
    )
    proposals.value = new Map(proposals.value).set(action.assistantMessageId, {
      ...action,
      status: result.status,
      result: result.result,
    })
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

function scrollToLatest() {
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

onMounted(loadChat)
</script>

<template>
  <section class="chat-panel" aria-label="Чат с управляющим агентом">
    <header class="panel-head">
      <div>
        <h2>Управляющий агент</h2>
        <p class="eyebrow">Планирование, расписание и импорт — в одном диалоге</p>
      </div>
      <button
        type="button"
        class="btn btn-ghost btn-small"
        :disabled="busy || loading"
        @click="startNewSession"
      >
        Новый диалог
      </button>
    </header>

    <p v-if="error" class="banner error" role="alert">{{ error }}</p>
    <p v-if="loading" class="status-line">Загрузка истории…</p>

    <div v-else ref="messageList" class="message-list" aria-live="polite">
      <p v-if="!messages.length" class="empty-state">
        Напишите, что нужно спланировать или изменить. Агент сначала объяснит решение,
        а изменение данных предложит отдельно для подтверждения.
      </p>
      <article
        v-for="message in messages"
        :key="message.id"
        class="message"
        :class="message.role === 'USER' ? 'message-user' : 'message-assistant'"
      >
        <div class="message-meta">
          {{ message.role === 'USER' ? 'Вы' : 'Агент' }}
          <time v-if="message.createdAt" :datetime="message.createdAt">{{ formatTime(message.createdAt) }}</time>
        </div>
        <p class="message-content">{{ message.content }}</p>

        <div v-if="proposalFor(message.id)" class="proposal-card">
          <div class="proposal-heading">{{ readableAction(proposalFor(message.id)) }}</div>
          <dl v-if="Object.keys(proposalFor(message.id).fields || {}).length" class="proposal-fields">
            <template v-for="(value, name) in proposalFor(message.id).fields" :key="name">
              <dt>{{ name }}</dt>
              <dd>{{ formatFieldValue(value) }}</dd>
            </template>
          </dl>
          <p v-if="proposalFor(message.id).targetId" class="proposal-target">
            Цель: #{{ proposalFor(message.id).targetId }}
          </p>
          <div class="proposal-actions">
            <button
              v-if="actionPresentation(proposalFor(message.id)).canConfirm"
              type="button"
              class="btn btn-primary"
              :disabled="busy"
              @click="confirmProposal(proposalFor(message.id))"
            >
              {{ busy ? 'Обработка…' : actionPresentation(proposalFor(message.id)).label }}
            </button>
            <button
              v-if="actionPresentation(proposalFor(message.id)).canConfirm"
              type="button"
              class="btn btn-ghost"
              :disabled="busy"
              @click="rejectProposal(proposalFor(message.id))"
            >
              Отклонить
            </button>
            <span
              v-else
              class="proposal-status"
              :class="{ applied: actionPresentation(proposalFor(message.id)).applied }"
            >
              {{ actionPresentation(proposalFor(message.id)).label }}
            </span>
          </div>
          <p v-if="proposalFor(message.id).result" class="proposal-result">
            Результат: {{ actionResultLabel(proposalFor(message.id).result) }}
          </p>
        </div>
      </article>
    </div>

    <form class="input-row" @submit.prevent="send">
      <textarea
        v-model="draft"
        class="chat-input"
        rows="3"
        maxlength="8000"
        placeholder="Напишите, что нужно сделать…"
        :disabled="busy || loading"
        @keydown.ctrl.enter.prevent="send"
      ></textarea>
      <button type="submit" class="btn btn-primary" :disabled="!canSend">
        {{ busy ? 'Ответ формируется…' : 'Отправить' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.chat-panel {
  display: grid;
  gap: var(--wolf-gap-group);
  width: min(var(--wolf-chat-width), calc(100vw - var(--wolf-chat-gutter)));
  background: var(--wolf-surface);
  border: 1px solid var(--wolf-rule);
  border-radius: var(--wolf-radius-lg);
  padding: var(--wolf-space-3) var(--wolf-space-4);
}
.panel-head { display: flex; justify-content: space-between; gap: var(--wolf-gap-inline); align-items: start; }
.panel-head h2 { margin: 0; font-size: var(--wolf-text-lg); font-weight: 600; }
.eyebrow { margin: var(--wolf-space-1) 0 0; color: var(--wolf-muted); font-size: var(--wolf-text-sm); }
.btn { font: inherit; cursor: pointer; border-radius: var(--wolf-radius-lg); padding: var(--wolf-space-2) var(--wolf-space-3); border: 1px solid transparent; }
.btn-primary { background: var(--wolf-ink); color: var(--wolf-on-ink); }
.btn-primary:disabled, .btn-ghost:disabled { opacity: 0.55; cursor: default; }
.btn-ghost { background: transparent; color: var(--wolf-muted); border-color: var(--wolf-rule); }
.btn-small { padding: var(--wolf-space-1) var(--wolf-space-2); white-space: nowrap; font-size: var(--wolf-text-sm); }
.banner { margin: 0; padding: var(--wolf-space-2) var(--wolf-space-3); border-radius: var(--wolf-radius-lg); font-size: var(--wolf-text-sm); }
.banner.error { background: var(--wolf-danger-surface); color: var(--wolf-danger-ink); border: 1px solid var(--wolf-danger-ink); }
.status-line, .empty-state { margin: 0; color: var(--wolf-muted); font-size: var(--wolf-text-sm); }
.message-list { display: grid; gap: var(--wolf-gap-inline); max-height: min(52vh, var(--wolf-chat-max-height)); overflow-y: auto; padding: var(--wolf-space-1) var(--wolf-space-2); }
.message { max-width: var(--wolf-chat-message-width); padding: var(--wolf-space-2) var(--wolf-space-3); border: 1px solid var(--wolf-rule); border-radius: var(--wolf-radius-lg); }
.message-user { justify-self: end; background: var(--wolf-surface); }
.message-assistant { justify-self: start; background: var(--wolf-fill); }
.message-meta { display: flex; gap: var(--wolf-space-2); color: var(--wolf-muted); font-size: var(--wolf-text-xs); }
.message-meta time { opacity: 0.75; }
.message-content { margin: var(--wolf-space-1) 0 0; white-space: pre-wrap; line-height: var(--wolf-leading-base); }
.proposal-card { margin-top: var(--wolf-space-2); padding-top: var(--wolf-space-2); border-top: 1px solid var(--wolf-rule); }
.proposal-heading { font-size: var(--wolf-text-sm); font-weight: 600; }
.proposal-fields { display: grid; grid-template-columns: minmax(var(--wolf-chat-label-width), 0.45fr) minmax(0, 1fr); gap: var(--wolf-space-1) var(--wolf-space-2); margin: var(--wolf-space-2) 0; font-size: var(--wolf-text-sm); }
.proposal-fields dt { color: var(--wolf-muted); }
.proposal-fields dd { margin: 0; overflow-wrap: anywhere; }
.proposal-target, .proposal-result { margin: var(--wolf-space-1) 0; color: var(--wolf-muted); font-size: var(--wolf-text-xs); }
.proposal-actions { display: flex; align-items: center; gap: var(--wolf-space-2); flex-wrap: wrap; }
.proposal-status { color: var(--wolf-muted); font-size: var(--wolf-text-sm); }
.proposal-status.applied { color: var(--wolf-done-ink); }
.input-row { display: grid; gap: var(--wolf-gap-inline); }
.chat-input { width: 100%; resize: vertical; box-sizing: border-box; font: inherit; padding: var(--wolf-space-2) var(--wolf-space-3); border: 1px solid var(--wolf-rule); border-bottom: 2px solid var(--wolf-rule); border-radius: var(--wolf-radius-lg); background: var(--wolf-surface); color: var(--wolf-ink); }
.chat-input:focus { outline: none; border-bottom-color: var(--wolf-focus); }
@media (max-width: 768px) {
  .chat-panel { width: calc(100vw - var(--wolf-chat-gutter)); padding: var(--wolf-space-3); }
  .panel-head { display: grid; }
  .message { max-width: var(--wolf-chat-message-width-mobile); }
}
</style>
