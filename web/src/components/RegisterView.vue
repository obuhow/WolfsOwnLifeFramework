<script setup>
import { onMounted, ref } from 'vue'
import { apiBase } from '../api'

const code = ref('')
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function register() {
  error.value = ''
  loading.value = true
  try {
    const res = await fetch(`${apiBase()}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: code.value, username: username.value, password: password.value })
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    const data = await res.json()
    localStorage.setItem('wolf_token', data.token)
    // Релиз 0.6, тикет 02: после регистрации по инвайту — строго на экран
    // выбора демо-профиля, а не на старый мастер /onboarding или /today.
    window.location.hash = '#/onboarding/profile'
    window.location.reload()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (localStorage.getItem('wolf_token')) window.location.hash = '#/today'
})
</script>

<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="register-title">
      <header class="login-header">
        <p class="eyebrow">WOLF</p>
        <h1 id="register-title">Регистрация по коду</h1>
      </header>

      <form class="login-form" @submit.prevent="register">
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <div class="form-group">
          <label for="code">Пригласительный код</label>
          <input id="code" v-model="code" class="input" type="text" autocomplete="off" :disabled="loading" required />
        </div>
        <div class="form-group">
          <label for="username">Имя пользователя</label>
          <input id="username" v-model="username" class="input" type="text" autocomplete="username" :disabled="loading" required />
        </div>
        <div class="form-group">
          <label for="password">Пароль</label>
          <input id="password" v-model="password" class="input" type="password" autocomplete="new-password" :disabled="loading" required minlength="8" />
        </div>
        <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
          {{ loading ? 'Регистрация…' : 'Зарегистрироваться' }}
        </button>
      </form>

      <p class="login-hint"><a href="#/login">Уже есть аккаунт? Войти</a></p>
    </section>
  </main>
</template>
