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
import { apiBase } from '../api'

// Поля входа пустые: предзаполнять форму учётными данными нельзя.
// Исторически здесь стояло admin/admin от однопользовательского режима до релиза 0.4;
// seed-аккаунт admin заблокирован с тикета 0.4/13, а на публичном стенде подсказка
// общеизвестного логина в форме — прямое приглашение к перебору.
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

// Демо-режим (тикет 07): список профилей + плашка с выданными кредами.
const demoOpen = ref(false)
const demoProfiles = ref([])
const demoLoading = ref(false)
const demoCreds = ref(null)

const FALLBACK_PROFILES = [
  { slug: 'worker-class', displayName: 'Рабочий класс' },
  { slug: 'wise-freelancer', displayName: 'Мудрый фрилансер' },
  { slug: 'free-artist', displayName: 'Свободный художник' }
]

async function openDemo() {
  demoOpen.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/demo/profiles`)
    demoProfiles.value = res.ok ? await res.json() : FALLBACK_PROFILES
  } catch {
    demoProfiles.value = FALLBACK_PROFILES
  }
}

async function startDemo(slug) {
  error.value = ''
  demoLoading.value = true
  try {
    const res = await fetch(`${apiBase()}/demo/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileSlug: slug })
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    const data = await res.json()
    localStorage.setItem('wolf_token', data.token)
    // Показать выданные креды перед входом (на случай повторного входа).
    demoCreds.value = { username: data.username, password: data.password }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    demoLoading.value = false
  }
}

function enterDemo() {
  window.location.hash = '#/today'
  window.location.reload()
}

async function login() {
  error.value = ''
  loading.value = true
  try {
    const res = await fetch(`${apiBase()}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value })
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    const data = await res.json()
    localStorage.setItem('wolf_token', data.token)
    window.location.hash = '#/life-areas'
    window.location.reload()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (localStorage.getItem('wolf_token')) window.location.hash = '#/life-areas'
})
</script>

<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="login-title">
      <header class="login-header">
        <p class="eyebrow">WOLF</p>
        <h1 id="login-title">Вход</h1>
      </header>

      <form class="login-form" @submit.prevent="login">
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <div class="form-group">
          <label for="username">Пользователь</label>
          <input id="username" v-model="username" class="input" type="text" autocomplete="username" :disabled="loading" required />
        </div>
        <div class="form-group">
          <label for="password">Пароль</label>
          <input id="password" v-model="password" class="input" type="password" autocomplete="current-password" :disabled="loading" required />
        </div>
        <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
          {{ loading ? 'Вход…' : 'Войти' }}
        </button>
      </form>

      <p class="login-hint"><a href="#/register">У меня есть код</a></p>

      <div class="demo-block">
        <button v-if="!demoOpen" type="button" class="btn btn-ghost btn-block" @click="openDemo">Демо-режим</button>

        <template v-else-if="!demoCreds">
          <p class="demo-lead">Выберите готовый профиль — WOLF создаст демо-пользователя с предзаполненными данными:</p>
          <div class="demo-profiles">
            <button
              v-for="profile in demoProfiles"
              :key="profile.slug"
              type="button"
              class="btn btn-ghost demo-profile"
              :disabled="demoLoading"
              @click="startDemo(profile.slug)"
            >{{ profile.displayName }}</button>
          </div>
          <p v-if="demoLoading" class="demo-lead">Создаём демо-профиль…</p>
        </template>

        <div v-else class="demo-creds">
          <p class="demo-lead">Демо-пользователь <strong>{{ demoCreds.username }}</strong>, пароль совпадает с логином — сохраните, чтобы войти повторно.</p>
          <button type="button" class="btn btn-primary btn-block" @click="enterDemo">Войти в демо</button>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.demo-block { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--wolf-subrule, #e5e5e5); }
.demo-lead { margin: 0 0 10px; color: var(--wolf-muted, #666); font-size: 13px; }
.demo-profiles { display: flex; flex-direction: column; gap: 8px; }
.demo-profile { width: 100%; }
.demo-creds { display: flex; flex-direction: column; gap: 10px; }
</style>
