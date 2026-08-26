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
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

const digest = ref(null)
const loading = ref(false)
const error = ref('')
const expanded = ref(new Set())

const hasContent = computed(() => digest.value && (digest.value.projects?.length || digest.value.ideas?.length || digest.value.goalsFact?.length))

function toggle(projectId) {
  const next = new Set(expanded.value)
  next.has(projectId) ? next.delete(projectId) : next.add(projectId)
  expanded.value = next
}

function formatHours(value) {
  if (value == null) return '—'
  const n = Number(value)
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
}

function categoryLabel(value) {
  return { BUSINESS: 'бизнес', MUSIC: 'музыкальная', PERSONAL: 'личная', CREEPY: 'криповая' }[value] || value
}

function authorLabel(value) {
  return value === 'AGENT' ? 'Агент' : 'Моя заметка'
}

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/morning-digest`, { headers })
    if (handleAuthFailure(res)) return
    if (!res.ok) throw new Error(`Утренний обход: HTTP ${res.status}`)
    digest.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="morning-page">
    <header class="page-header">
      <h1>Утренний обход</h1>
      <p class="eyebrow">Управление потоком · один спокойный взгляд на то, что сейчас живо</p>
    </header>

    <div v-if="error" class="alert alert-error">{{ error }}</div>
    <div v-if="loading" class="loading">Загрузка обхода…</div>

    <template v-else-if="digest">
      <section class="card morning-projects">
        <div class="projects-toolbar">
          <h2>Проекты</h2>
          <span class="muted">{{ digest.projects.length }}</span>
        </div>
        <div v-if="!digest.projects.length" class="muted-block">Активных проектов пока нет.</div>
        <article v-for="project in digest.projects" :key="project.id" class="morning-project">
          <div class="morning-project-heading">
            <router-link :to="`/projects/${project.id}`" class="morning-project-link">{{ project.title }}</router-link>
            <button class="btn btn-ghost" :aria-expanded="expanded.has(project.id)" @click="toggle(project.id)">
              {{ expanded.has(project.id) ? 'Свернуть' : 'Развернуть' }}
            </button>
          </div>
          <div v-if="expanded.has(project.id)" class="morning-project-detail">
            <div>
              <h3>Последние заметки</h3>
              <div v-if="!project.lastNotes.length" class="muted-block">Заметок пока нет.</div>
              <div v-for="note in project.lastNotes" :key="note.id" class="morning-note" :class="{ 'morning-note-agent': note.author === 'AGENT' }">
                <div class="morning-note-meta">{{ authorLabel(note.author) }} · {{ note.createdAt ? new Date(note.createdAt).toLocaleString('ru-RU') : '' }}</div>
                <div>{{ note.body }}</div>
              </div>
            </div>
            <div>
              <h3>Дела</h3>
              <div v-if="!project.topDelos.length" class="muted-block">Дел в проекте пока нет.</div>
              <router-link v-for="delo in project.topDelos" :key="delo.id" :to="`/delos/${delo.id}`" class="morning-delo">{{ delo.title }}</router-link>
            </div>
          </div>
        </article>
      </section>

      <section class="card morning-ideas">
        <h2>Идеи для всплытия</h2>
        <div v-if="!digest.ideas.length" class="muted-block">В банке пока нет идей.</div>
        <div v-else class="morning-idea-grid">
          <router-link v-for="idea in digest.ideas" :key="idea.id" :to="`/ideas/${idea.id}`" class="morning-idea">
            <strong>{{ idea.title }}</strong>
            <span>{{ categoryLabel(idea.category) }}</span>
            <p v-if="idea.description">{{ idea.description }}</p>
          </router-link>
        </div>
      </section>

      <section class="card morning-goals">
        <div class="projects-toolbar">
          <h2>Неделя по целям</h2>
          <span class="muted">{{ digest.weekId }}</span>
        </div>
        <div v-if="!digest.goalsFact.length" class="muted-block">Активных целей пока нет.</div>
        <div v-for="goal in digest.goalsFact" :key="goal.goalId" class="goal-fact-row">
          <span class="goal-fact-title">{{ goal.title }}</span>
          <span class="goal-fact-hours">{{ formatHours(goal.factHours) }} / {{ formatHours(goal.budgetHours) }} ч</span>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.morning-project + .morning-project { border-top: 1px solid var(--wolf-subrule); }
.morning-project-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.morning-project-link, .morning-delo { color: var(--wolf-ink); text-decoration: none; border-bottom: 1px solid var(--wolf-rule); }
.morning-project-link { font-size: 18px; }
.morning-project-detail { display: grid; grid-template-columns: minmax(0, 2fr) minmax(180px, 1fr); gap: 28px; padding: 16px 0 20px; }
.morning-project-detail h3 { margin: 0 0 8px; font-size: 12px; font-weight: 600; }
.morning-note { padding: 10px 0; border-top: 1px solid var(--wolf-subrule); white-space: pre-wrap; }
.morning-note-agent { border-left: 2px solid var(--wolf-ink); padding-left: 10px; }
.morning-note-agent .morning-note-meta { color: var(--wolf-ink); }
.morning-note-meta { margin-bottom: 4px; color: var(--wolf-muted); font-size: 11px; }
.morning-delo { display: block; width: fit-content; margin: 0 0 8px; }
.morning-idea-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.morning-idea { display: block; padding: 14px 0; color: var(--wolf-ink); text-decoration: none; border-top: 1px solid var(--wolf-rule); }
.morning-idea strong, .morning-idea span { display: block; }
.morning-idea span { margin-top: 4px; color: var(--wolf-muted); font-size: 11px; }
.morning-idea p { margin: 8px 0 0; color: var(--wolf-muted); }
.goal-fact-row { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; padding: 8px 0; border-bottom: 1px solid var(--wolf-subrule); }
.goal-fact-title { color: var(--wolf-ink); font-size: 13px; }
.goal-fact-hours { color: var(--wolf-muted); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
@media (max-width: 700px) { .morning-project-detail, .morning-idea-grid { grid-template-columns: 1fr; } }
</style>
