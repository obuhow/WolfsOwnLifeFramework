<script setup>
import { onMounted, ref } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from './components/LoginView.vue'
import SettingsView from './components/SettingsView.vue'
import { apiBase } from './api'

const token = ref(localStorage.getItem('wolf_token') || '')
const healthStatus = ref('…')
const healthError = ref('')

const routes = [
  { path: '/', component: { template: '<div class="shell"><header class="header"><p class="eyebrow">WOLF</p><h1>Уютно. Тихо. Под вашим контролем.</h1></header><main class="main"><section class="card"><h2>WOLF 0.1</h2><p>Главный экран (пока пустой). Перейдите в <a href="#/settings">Настройки</a>.</p></section></main></div>' } },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/settings', component: SettingsView }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.public && !token.value) {
    return '/login'
  }
  if (to.path === '/login' && token.value) {
    return '/'
  }
})

onMounted(async () => {
  try {
    const res = await fetch(`${apiBase()}/health`)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    const body = await res.json()
    healthStatus.value = body.status ?? JSON.stringify(body)
  } catch (e) {
    healthStatus.value = 'недоступен'
    healthError.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <div id="app">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>

    <footer v-if="!token" class="global-footer">
      <p>API: <code>{{ apiBase() }}</code> — <strong :class="healthStatus === 'UP' ? 'ok' : 'bad'">{{ healthStatus }}</strong></p>
    </footer>
  </div>
</template>

<style>
@import './style.css';

#app {
  min-height: 100vh;
}

.global-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0.75rem 1.5rem;
  text-align: center;
  font-size: 0.75rem;
  color: #8a8278;
  background: rgba(244, 241, 236, 0.95);
  border-top: 1px solid #e6dfd4;
}

.global-footer code {
  background: #f0ebe3;
  padding: 0.1rem 0.35rem;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
}

.global-footer .ok {
  color: #2f6b4f;
}

.global-footer .bad {
  color: #8a4b2f;
}
</style>