<script setup>
import { ref, onMounted } from 'vue'
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

function handleKey(e) {
  if (e.key === 'Enter') login()
}

onMounted(() => {
  if (localStorage.getItem('wolf_token')) {
    window.location.hash = '#/life-areas'
  }
})
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <header class="login-header">
        <p class="eyebrow">WOLF</p>
        <h1>Вход</h1>
      </header>

      <form @submit.prevent="login" class="login-form">
        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="form-group">
          <label for="username">Пользователь</label>
          <input
            id="username"
            type="text"
            v-model="username"
            :disabled="loading"
            @keyup="handleKey"
            class="input"
            autocomplete="username"
            required
          />
        </div>

        <div class="form-group">
          <label for="password">Пароль</label>
          <input
            id="password"
            type="password"
            v-model="password"
            :disabled="loading"
            @keyup="handleKey"
            class="input"
            autocomplete="current-password"
            required
          />
        </div>

        <button
          type="submit"
          :disabled="loading"
          class="btn btn-primary btn-block"
        >
          <span v-if="loading">Вход…</span>
          <span v-else>Войти</span>
        </button>
      </form>

      <p class="login-hint">Seed: <code>admin</code> / <code>admin</code></p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.login-card {
  width: 100%;
  max-width: 360px;
  background: rgba(255, 252, 247, 0.95);
  border: 1px solid #e6dfd4;
  border-radius: 18px;
  padding: 2rem;
  box-shadow: 0 20px 50px rgba(70, 55, 30, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 1.75rem;
}

.login-header .eyebrow {
  margin: 0 0 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.75rem;
  color: #7a7268;
}

.login-header h1 {
  margin: 0;
  font-size: 1.8rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.login-form {
  display: grid;
  gap: 1.25rem;
}

.form-group {
  display: grid;
  gap: 0.4rem;
}

.form-group label {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #8a8278;
  font-weight: 500.
}

.input {
  font-family: inherit;
  font-size: 1rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid #d9d1c6;
  border-radius: 10px;
  background: #fdfbf7;
  color: #2c2a26;
  transition: border-color 0.15s, box-shadow 0.15s.
}

.input:focus {
  outline: none;
  border-color: #a89a88;
  box-shadow: 0 0 0 3px rgba(168, 154, 136, 0.15).
}

.input:disabled {
  opacity: 0.6;
  cursor: not-allowed.
}

.btn {
  font-family: inherit;
  font-size: 1rem;
  font-weight: 500;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, transform 0.05s;
  margin-top: 0.25rem;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed.
}

.btn-primary {
  background: #3d5a4a;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #2f4a3c.
}

.btn-block {
  width: 100%;
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.9rem;
}

.alert-error {
  background: #fdeaea;
  border: 1px solid #e8b4b4;
  color: #8a3a3a.
}

.login-hint {
  margin: 1.5rem 0 0;
  text-align: center;
  font-size: 0.8rem;
  color: #7a7268.
}

.login-hint code {
  background: #f0ebe3;
  padding: 0.1rem 0.35rem;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
}
</style>