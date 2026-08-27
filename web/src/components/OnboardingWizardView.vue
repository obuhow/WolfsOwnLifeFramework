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
 * /onboarding — мастер первого входа: Проект → Цель → Норма (релиз 0.4, тикет 08).
 *
 * API мастера (`/onboarding/step1..step3`, `/complete`, `/skip`) существует с
 * релиза 0.4, а экран к нему не был написан. Ветка «Очистить профиль» Финального
 * выбора (тикет 04) обязана куда-то приводить — иначе гость после очистки попадал
 * бы на несуществующий маршрут и оставался в пустой системе.
 *
 * Сохранение пошаговое: закрытая на втором шаге вкладка не теряет созданный на
 * первом Проект. Визуальный контракт 0.3 — один вопрос на экран, поле с нижним
 * правилом, текстовые действия с подчёркиванием, без прогресс-бара.
 */
import { ref, computed } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

// Шаги мастера, а не «шаг N из M»: счётчик прогресса тихий контракт запрещает.
const step = ref(1)
const busy = ref(false)
const error = ref('')

const projectTitle = ref('')
const projectId = ref(null)

const goalTitle = ref('')
const goalWeeklyHours = ref('4')

const availableWeeklyHours = ref('30')

const canSubmit = computed(() => {
  if (busy.value) return false
  if (step.value === 1) return projectTitle.value.trim().length > 0
  if (step.value === 2) return goalTitle.value.trim().length > 0 && Number(goalWeeklyHours.value) >= 0.25
  return Number(availableWeeklyHours.value) >= 1
})

async function post(path, body) {
  const headers = authHeaders(true)
  if (!headers) return null
  const res = await fetch(`${apiBase()}/onboarding/${path}`, {
    method: 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (handleAuthFailure(res)) return null
  if (!res.ok) {
    const text = await res.text()
    let message = `HTTP ${res.status}`
    try {
      message = JSON.parse(text).message || message
    } catch (e) {
      if (text) message = text
    }
    throw new Error(message)
  }
  return res.json().catch(() => ({}))
}

async function next() {
  if (!canSubmit.value) return
  busy.value = true
  error.value = ''
  try {
    if (step.value === 1) {
      const data = await post('step1/project', { title: projectTitle.value.trim() })
      if (!data) return
      projectId.value = data.projectId
      step.value = 2
    } else if (step.value === 2) {
      const data = await post('step2/goal', {
        projectId: projectId.value,
        title: goalTitle.value.trim(),
        weeklyHours: Number(goalWeeklyHours.value),
      })
      if (!data) return
      step.value = 3
    } else {
      if (!(await post('step3/weekly-hours', { weeklyHours: Number(availableWeeklyHours.value) }))) return
      if (!(await post('complete'))) return
      window.location.hash = '#/today'
      // Гейт онбординга кеширует статус — перезагрузка снимает кеш.
      window.location.reload()
      return
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function skip() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (!(await post('skip'))) return
    window.location.hash = '#/today'
    window.location.reload()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    busy.value = false
  }
}
</script>

<template>
  <main class="onboarding-page">
    <section class="onboarding-inner" aria-labelledby="wizard-title">
      <header class="onboarding-head">
        <p class="eyebrow">WOLF</p>
        <h1 id="wizard-title">Первый вход</h1>
      </header>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

      <form class="wizard-step" data-wizard-step="1" v-if="step === 1" @submit.prevent="next">
        <label class="field">
          <span class="field-label">Название проекта</span>
          <input
            v-model="projectTitle"
            type="text"
            maxlength="200"
            autofocus
            placeholder="Например: Ремонт кухни"
          />
        </label>
        <p class="field-hint">
          Проект — то, что тянется дольше одного дня и состоит из Дел.
        </p>
        <p class="wizard-actions">
          <button type="submit" class="text-action" :disabled="!canSubmit">Далее</button>
          <button type="button" class="text-action muted" :disabled="busy" @click="skip">Пропустить</button>
        </p>
      </form>

      <form class="wizard-step" data-wizard-step="2" v-else-if="step === 2" @submit.prevent="next">
        <label class="field">
          <span class="field-label">Что вы хотите продвинуть?</span>
          <input
            v-model="goalTitle"
            type="text"
            maxlength="200"
            autofocus
            placeholder="Например: Закончить кухню до конца квартала"
          />
        </label>
        <label class="field">
          <span class="field-label">Недельный бюджет часов на эту Цель</span>
          <input v-model="goalWeeklyHours" type="number" min="0.25" step="0.25" />
        </label>
        <p class="field-hint">Цель связывается с проектом «{{ projectTitle.trim() }}».</p>
        <p class="wizard-actions">
          <button type="submit" class="text-action" :disabled="!canSubmit">Далее</button>
          <button type="button" class="text-action muted" :disabled="busy" @click="skip">Пропустить</button>
        </p>
      </form>

      <form class="wizard-step" data-wizard-step="3" v-else @submit.prevent="next">
        <label class="field">
          <span class="field-label">Сколько часов в неделю у вас реально есть?</span>
          <input v-model="availableWeeklyHours" type="number" min="1" step="0.5" autofocus />
        </label>
        <p class="field-hint">
          Это норма недели: «Сегодня» показывает по ней остаток. Меняется в Настройках.
        </p>
        <p class="wizard-actions">
          <button type="submit" class="text-action" :disabled="!canSubmit">Готово</button>
          <button type="button" class="text-action muted" :disabled="busy" @click="skip">Пропустить</button>
        </p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  padding: 64px 24px;
  background: var(--wolf-surface, #ffffff);
}

.onboarding-inner {
  width: 100%;
  max-width: 640px;
}

.onboarding-head {
  margin-bottom: 32px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--wolf-faint, #a3a3a3);
}

.onboarding-head h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.wizard-step {
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
  padding-top: 24px;
}

.field {
  display: block;
  margin-bottom: 20px;
}

.field-label {
  display: block;
  font-size: 13px;
  color: var(--wolf-muted, #737373);
  margin-bottom: 6px;
}

.field input {
  width: 100%;
  padding: 6px 0;
  font-size: 15px;
  color: var(--wolf-ink, #1a1a1a);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
  border-radius: 0;
  box-shadow: none;
}

.field input:focus {
  outline: none;
  border-bottom-color: var(--wolf-ink, #1a1a1a);
}

.field-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--wolf-muted, #737373);
  max-width: 52ch;
}

.wizard-actions {
  margin: 28px 0 0;
  display: flex;
  gap: 20px;
  align-items: baseline;
}

.text-action {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
  font-size: 13px;
  color: var(--wolf-ink, #1a1a1a);
  text-decoration: underline;
  cursor: pointer;
}

.text-action.muted {
  color: var(--wolf-muted, #737373);
}

.text-action:disabled {
  color: var(--wolf-faint, #a3a3a3);
  cursor: default;
}

.text-action:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: 2px;
}
</style>
