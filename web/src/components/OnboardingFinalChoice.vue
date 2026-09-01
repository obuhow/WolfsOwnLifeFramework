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
 * /onboarding/final — Финальный выбор Знакомства (релиз 0.6, тикет 04).
 *
 * Две ветки, без подтверждающего второго диалога:
 *   «Оставить» → POST /onboarding/complete → /calendar с демо-данными на месте.
 *   «Очистить» → POST /onboarding/purge-profile → /onboarding — мастер первого
 *                входа (0.4-08), чтобы гость не оказался в пустой системе.
 *
 * Флаг `onboardingCompletedAt` в ветке очистки не ставится: его проставит сам
 * мастер по завершении. Тур сюда приводит и при финише, и при прерывании.
 */
import { ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'
import { endTour } from '../onboardingTour'

const busy = ref('')
const error = ref('')

async function post(path) {
  const headers = authHeaders(true)
  if (!headers) return null
  const res = await fetch(`${apiBase()}/onboarding/${path}`, { method: 'POST', headers })
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

async function keep() {
  if (busy.value) return
  busy.value = 'keep'
  error.value = ''
  try {
    if (!(await post('complete'))) return
    endTour()
    window.location.hash = '#/calendar'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    busy.value = ''
  }
}

async function purge() {
  if (busy.value) return
  busy.value = 'purge'
  error.value = ''
  try {
    if (!(await post('purge-profile'))) return
    endTour()
    window.location.hash = '#/onboarding'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    busy.value = ''
  }
}
</script>

<template>
  <main class="onboarding-page">
    <section class="onboarding-inner" aria-labelledby="final-choice-title">
      <header class="onboarding-head">
        <p class="eyebrow">WOLF</p>
        <h1 id="final-choice-title">Приветственный тур завершён</h1>
        <p class="subtitle">
          Сейчас в системе демо-данные выбранного профиля. Их можно оставить и разбираться
          на живом примере — или очистить и начать со своих Проектов и Целей.
        </p>
      </header>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

      <ul class="choice-list">
        <li>
          <button
            type="button"
            class="choice-option"
            data-final-choice="keep"
            :disabled="!!busy"
            @click="keep"
          >
            <span class="choice-name">Оставить предзаполненный профиль</span>
            <span class="choice-desc">
              Проекты, Дела, Записи времени, Цели, Идеи и Заметки демо-профиля остаются.
              Их можно править и удалять как любые свои.
            </span>
            <span v-if="busy === 'keep'" class="choice-status">Сохраняю…</span>
          </button>
        </li>
        <li>
          <button
            type="button"
            class="choice-option"
            data-final-choice="purge"
            :disabled="!!busy"
            @click="purge"
          >
            <span class="choice-name">Очистить профиль</span>
            <span class="choice-desc">
              Демо-данные удаляются, Области жизни и Сферы остаются, недельная норма
              возвращается к 30 часам. Дальше — короткий мастер: Проект, Цель, норма.
            </span>
            <span v-if="busy === 'purge'" class="choice-status">Очищаю…</span>
          </button>
        </li>
      </ul>
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
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.subtitle {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--wolf-muted, #737373);
  max-width: 52ch;
}

.choice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.choice-list li {
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.choice-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 20px 4px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.choice-option:hover:not(:disabled) {
  background: var(--wolf-hover, #f7f7f5);
}

.choice-option:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: -2px;
}

.choice-option:disabled {
  cursor: default;
}

.choice-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.choice-desc {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--wolf-muted, #737373);
}

.choice-status {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--wolf-faint, #a3a3a3);
}
</style>
