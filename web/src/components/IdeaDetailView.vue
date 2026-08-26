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
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

const route = useRoute()
const idea = ref(null)
const loading = ref(false)
const error = ref('')

const categoryLabels = { BUSINESS: 'Бизнес', MUSIC: 'Музыкальные', PERSONAL: 'Личные', CREEPY: 'Криповые' }
const statusLabels = { BANK: 'В банке', IN_WORK: 'В работе', ARCHIVED: 'Архив' }

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/ideas/${route.params.id}`, { headers })
    if (handleAuthFailure(res)) return
    if (!res.ok) throw new Error(`Идея: HTTP ${res.status}`)
    idea.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="idea-detail-page">
    <header class="page-header">
      <router-link to="/ideas" class="back-link">← Банк идей</router-link>
      <h1 v-if="idea">{{ idea.title }}</h1>
      <h1 v-else>Идея</h1>
      <p v-if="idea" class="eyebrow">{{ categoryLabels[idea.category] || idea.category }} · {{ statusLabels[idea.status] || idea.status }}</p>
    </header>

    <div v-if="loading" class="loading">Загрузка идеи…</div>
    <div v-else-if="error" class="alert alert-error">{{ error }}</div>
    <section v-else-if="idea" class="card">
      <p v-if="idea.description" class="idea-description">{{ idea.description }}</p>
      <p v-else class="muted-block">Описание не добавлено.</p>
      <router-link v-if="idea.promotedProjectId" :to="`/projects/${idea.promotedProjectId}`" class="btn btn-ghost">
        Открыть Проект «{{ idea.promotedProjectTitle }}»
      </router-link>
    </section>
  </div>
</template>

<style scoped>
.back-link { display: inline-block; margin-bottom: 14px; color: var(--wolf-muted); text-decoration: none; }
.idea-description { max-width: 760px; white-space: pre-wrap; color: var(--wolf-ink); }
</style>
