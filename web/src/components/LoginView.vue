<script setup>
import { onMounted, ref } from 'vue'
import { apiBase } from '../api'

const username = ref('admin')
const password = ref('admin')
const error = ref('')
const loading = ref(false)

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

      <p class="login-hint">Seed: <code>admin</code> / <code>admin</code></p>
    </section>
  </main>
</template>
