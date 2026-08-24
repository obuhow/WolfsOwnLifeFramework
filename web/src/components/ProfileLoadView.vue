<script setup>
/**
 * /onboarding/profile — первый экран после регистрации по инвайту.
 * Гость выбирает один из трёх демо-профилей; клик загружает демо-конфигурацию
 * (POST /api/v1/onboarding/load-profile, тикет 01) и уводит на тур (/onboarding/tour,
 * тикет 03). Визуальный контракт тихого регистра 0.3 — без ярких карточек с тенями.
 */
import { ref } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

const PROFILES = [
  {
    slug: 'worker-class',
    displayName: 'Рабочий класс',
    description:
      'Наёмный работник, восемь-двенадцать часов в день на основной работе. WOLF помогает ему выкроить свободные часы вокруг смены: ремонт кухни, английский до B2, подготовка к отпуску. Норма — 15 часов в неделю.',
  },
  {
    slug: 'wise-freelancer',
    displayName: 'Мудрый фрилансер',
    description:
      'Пять-шесть проектов разом — коммерческие заказы и один pet-проект, все дерутся за одно и то же время. Он вечно разрывается между клиентами и делает выводы в заметках, чтобы в следующий раз заложить буфер. Норма — 30 часов в неделю.',
  },
  {
    slug: 'free-artist',
    displayName: 'Свободный художник',
    description:
      'Работает рвано, не по графику: недели тишины сменяются плотными сериями. Полный Банк идей — что-то реализовано, что-то заброшено, и это нормально. Норма — 10 часов в неделю.',
  },
]

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

      <ul class="profile-list">
        <li v-for="profile in PROFILES" :key="profile.slug">
          <button
            type="button"
            class="profile-option"
            :disabled="!!loadingSlug"
            :aria-busy="loadingSlug === profile.slug ? 'true' : 'false'"
            @click="loadProfile(profile.slug)"
          >
            <span class="profile-name">{{ profile.displayName }}</span>
            <span class="profile-desc">{{ profile.description }}</span>
            <span v-if="loadingSlug === profile.slug" class="profile-status">Загружаю…</span>
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

.profile-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.profile-list li {
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.profile-option {
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

.profile-option:hover:not(:disabled) {
  background: var(--wolf-hover, #f7f7f5);
}

.profile-option:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: -2px;
}

.profile-option:disabled {
  cursor: default;
}

.profile-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.profile-desc {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--wolf-muted, #737373);
}

.profile-status {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--wolf-faint, #a3a3a3);
}
</style>
