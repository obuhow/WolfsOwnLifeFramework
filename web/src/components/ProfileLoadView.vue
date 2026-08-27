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
 * /onboarding/profile — первый экран после регистрации по инвайту.
 * Гость выбирает один из трёх демо-профилей; клик загружает демо-конфигурацию
 * (POST /api/v1/onboarding/load-profile, тикет 01) и уводит на тур (/onboarding/tour,
 * тикет 03). Визуальный контракт тихого регистра 0.3 — без ярких карточек с тенями.
 *
 * Вёрстка карточек — в `ProfileChoiceList.vue`, общая с блоком «Демо-профиль»
 * в Настройках (тикет 05).
 */
import { ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'
import ProfileChoiceList from './ProfileChoiceList.vue'

const loadingSlug = ref('')
const error = ref('')

async function loadProfile(slug) {
  if (loadingSlug.value) return
  error.value = ''
  loadingSlug.value = slug
  const headers = authHeaders(true)
  if (!headers) return
  try {
    const res = await fetch(`${apiBase()}/onboarding/load-profile`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ slug }),
    })
    if (handleAuthFailure(res)) return
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    // Профиль загружен — на тур (тикет 03).
    window.location.hash = '#/onboarding/tour'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    loadingSlug.value = ''
  }
}
</script>

<template>
  <main class="onboarding-page">
    <section class="onboarding-inner" aria-labelledby="profile-load-title">
      <header class="onboarding-head">
        <p class="eyebrow">WOLF</p>
        <h1 id="profile-load-title">Загрузка профиля</h1>
        <p class="subtitle">
          Посмотри, как можно использовать WOLF. Выбери пункт, чтобы загрузить демо-конфигурацию.
        </p>
      </header>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

      <ProfileChoiceList :busy-slug="loadingSlug" @select="loadProfile" />
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
</style>
