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
/**
 * Администрирование → Пользователи (релиз 1.0, тикет 06).
 * Видно только для role === 'ADMIN'. Список пользователей с действиями
 * блокировка/разблокировка/удаление, фильтр «включая демо».
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiBase, authHeaders } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const router = useRouter()
const authorized = ref(false)
const checkingRole = ref(true)

const users = ref([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const includeDemo = ref(true)

const activeUsers = computed(() => users.value.filter(u => u.status === 'ACTIVE'))
const blockedUsers = computed(() => users.value.filter(u => u.status === 'BLOCKED'))
const demoUsers = computed(() => users.value.filter(u => u.accountType === 'DEMO'))

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const url = `${apiBase()}/admin/users?includeDemo=${includeDemo.value}`
    const res = await fetch(url, { headers })
    if (!res.ok) throw new Error(`Пользователи: HTTP ${res.status}`)
    users.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function blockUser(user) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/admin/users/${user.id}/block`, { method: 'POST', headers })
    if (!res.ok) throw new Error(`Блокировка: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function unblockUser(user) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/admin/users/${user.id}/unblock`, { method: 'POST', headers })
    if (!res.ok) throw new Error(`Разблокировка: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function deleteUser(user) {
  const headers = authHeaders(true)
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/admin/users/${user.id}`, {
      method: 'DELETE',
      headers,
      body: JSON.stringify({ confirmUsername: user.username })
    })
    if (!res.ok) throw new Error(`Удаление: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
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
  <div v-if="authorized" class="admin-users-page">
    <header class="page-header admin-users-header">
      <div>
        <h1>Администрирование</h1>
        <p class="eyebrow">Пользователи · блокировка, удаление, фильтр демо</p>
      </div>
    </header>

    <p v-if="error" class="alert alert-error">{{ error }}</p>

    <div class="admin-users-toolbar">
      <label class="checkbox-inline">
        <input type="checkbox" v-model="includeDemo" @change="load" />
        <span>Включая демо-пользователей</span>
      </label>
    </div>

    <div v-if="loading" class="loading">Загрузка…</div>

    <template v-else>
      <div v-if="demoUsers.length" class="admin-users-section">
        <h2 class="admin-users-subheading">Демо-пользователи</h2>
        <table class="admin-users-table">
          <thead>
            <tr>
              <th>Логин</th>
              <th>Статус</th>
              <th>Создан</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in demoUsers" :key="u.id" :class="['admin-user-row', u.status.toLowerCase()]">
              <td><code>{{ u.username }}</code></td>
              <td><span class="status-badge" :class="u.status.toLowerCase()">{{ u.status }}</span></td>
              <td>{{ formatDate(u.createdAt) }}</td>
              <td>
                <div class="admin-user-actions">
                  <ConfirmInline
                    v-if="u.status === 'ACTIVE'"
                    label="Заблокировать"
                    :question="`Заблокировать «${u.username}»?`"
                    confirm-label="Да, заблокировать"
                    :disabled="busy"
                    @confirm="blockUser(u)"
                  />
                  <ConfirmInline
                    v-else
                    label="Разблокировать"
                    :question="`Разблокировать «${u.username}»?`"
                    confirm-label="Да, разблокировать"
                    :disabled="busy"
                    @confirm="unblockUser(u)"
                  />
                  <ConfirmInline
                    label="Удалить"
                    :question="`Удалить «${u.username}» вместе со всеми данными? Необратимо.`"
                    confirm-label="Да, удалить"
                    :disabled="busy"
                    @confirm="deleteUser(u)"
                  />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="activeUsers.length || blockedUsers.length" class="admin-users-section">
        <h2 class="admin-users-subheading">Обычные пользователи</h2>
        <table class="admin-users-table">
          <thead>
            <tr>
              <th>Логин</th>
              <th>Роль</th>
              <th>Статус</th>
              <th>Создан</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in activeUsers.concat(blockedUsers)" :key="u.id" :class="['admin-user-row', u.status.toLowerCase()]">
              <td><code>{{ u.username }}</code></td>
              <td>{{ u.role }}</td>
              <td><span class="status-badge" :class="u.status.toLowerCase()">{{ u.status }}</span></td>
              <td>{{ formatDate(u.createdAt) }}</td>
              <td>
                <div class="admin-user-actions">
                  <ConfirmInline
                    v-if="u.status === 'ACTIVE'"
                    label="Заблокировать"
                    :question="`Заблокировать «${u.username}»?`"
                    confirm-label="Да, заблокировать"
                    :disabled="busy"
                    @confirm="blockUser(u)"
                  />
                  <ConfirmInline
                    v-else
                    label="Разблокировать"
                    :question="`Разблокировать «${u.username}»?`"
                    confirm-label="Да, разблокировать"
                    :disabled="busy"
                    @confirm="unblockUser(u)"
                  />
                  <ConfirmInline
                    v-if="u.role !== 'ADMIN'"
                    label="Удалить"
                    :question="`Удалить «${u.username}»? Необратимо.`"
                    confirm-label="Да, удалить"
                    :disabled="busy"
                    @confirm="deleteUser(u)"
                  />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="!users.length" class="hint">Пользователей не найдено.</p>
    </template>
  </div>
</template>

<style scoped>
.admin-users-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.admin-users-toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 8px 0;
  border-top: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 16px;
}

.checkbox-inline {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--wolf-ink);
  font-size: 13px;
  cursor: pointer;
}

.admin-users-section {
  margin-bottom: 24px;
}

.admin-users-subheading {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--wolf-ink);
}

.admin-users-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.admin-users-table th,
.admin-users-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--wolf-rule);
}

.admin-users-table th {
  color: var(--wolf-muted);
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.admin-users-table tbody tr:hover {
  background: var(--wolf-card);
}

.admin-user-row code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.status-badge.active {
  background: var(--wolf-success-bg, #e8f5e9);
  color: var(--wolf-success, #2e7d32);
}

.status-badge.blocked {
  background: var(--wolf-error-bg, #fde8e8);
  color: var(--wolf-error, #c62828);
}

.admin-user-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 800px) {
  .admin-users-table thead {
    display: none;
  }
  .admin-users-table tbody tr {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4px 12px;
    padding: 12px;
    border: 1px solid var(--wolf-rule);
    border-radius: 4px;
    margin-bottom: 8px;
  }
  .admin-users-table td {
    border: none;
    padding: 2px 0;
    display: contents;
  }
  .admin-users-table td:first-child::before { content: 'Логин'; color: var(--wolf-muted); font-size: 11px; }
  .admin-users-table td:nth-child(2)::before { content: 'Роль'; color: var(--wolf-muted); font-size: 11px; }
  .admin-users-table td:nth-child(3)::before { content: 'Статус'; color: var(--wolf-muted); font-size: 11px; }
  .admin-users-table td:nth-child(4)::before { content: 'Создан'; color: var(--wolf-muted); font-size: 11px; }
  .admin-users-table td:nth-child(5)::before { content: 'Действия'; color: var(--wolf-muted); font-size: 11px; }
  .admin-user-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--wolf-rule);
  }
}
</style>