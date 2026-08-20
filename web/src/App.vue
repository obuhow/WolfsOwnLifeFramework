<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { apiBase } from './api'

const router = useRouter()
const route = useRoute()
const token = ref(localStorage.getItem('wolf_token') || '')
const username = ref('')

async function loadUser() {
  const t = localStorage.getItem('wolf_token')
  if (!t) return
  try {
    const res = await fetch(`${apiBase()}/auth/me`, {
      headers: { 'Authorization': `Bearer ${t}` }
    })
    if (res.status === 401 || res.status === 403) {
      localStorage.removeItem('wolf_token')
      token.value = ''
      username.value = ''
      router.push('/login')
      return
    }
    if (res.ok) {
      const data = await res.json()
      username.value = data.username || 'admin'
    }
  } catch (e) {
    // ignore network blips
  }
}

async function logout() {
  localStorage.removeItem('wolf_token')
  token.value = ''
  username.value = ''
  router.push('/login')
}

// Keep token in sync after login (login does full page reload currently)
watch(() => route.path, () => {
  token.value = localStorage.getItem('wolf_token') || ''
})

onMounted(async () => {
  token.value = localStorage.getItem('wolf_token') || ''
  if (token.value) {
    await loadUser()
  }
})
</script>

<template>
  <div id="app-root">
    <div v-if="token" class="app-shell">
      <header class="app-header">
        <div class="header-inner">
          <router-link to="/today" class="brand" aria-label="WOLF — Главная">
            <span class="brand-logo">WOLF</span>
            <span class="brand-tagline">Уютно. Тихо. Под вашим контролем.</span>
          </router-link>

          <nav class="nav" aria-label="Основная навигация">
            <router-link
              to="/morning"
              class="nav-link"
              :class="{ active: route.path === '/morning' }"
            >Утро</router-link>
            <router-link
              to="/today"
              class="nav-link"
              :class="{ active: route.path === '/today' }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
              Сегодня
            </router-link>
            <router-link
              to="/calendar"
              class="nav-link"
              :class="{ active: route.path === '/calendar' }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
                <line x1="8" y1="14" x2="8" y2="14.01"></line>
                <line x1="12" y1="14" x2="12" y2="14.01"></line>
                <line x1="16" y1="14" x2="16" y2="14.01"></line>
                <line x1="8" y1="18" x2="8" y2="18.01"></line>
                <line x1="12" y1="18" x2="12" y2="18.01"></line>
              </svg>
              Календарь
            </router-link>
            <router-link
              to="/planning"
              class="nav-link"
              :class="{ active: route.path === '/planning' }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="8" y1="6" x2="21" y2="6"></line>
                <line x1="8" y1="12" x2="21" y2="12"></line>
                <line x1="8" y1="18" x2="21" y2="18"></line>
                <line x1="3" y1="6" x2="3.01" y2="6"></line>
                <line x1="3" y1="12" x2="3.01" y2="12"></line>
                <line x1="3" y1="18" x2="3.01" y2="18"></line>
              </svg>
              Планирование
            </router-link>
            <router-link
              to="/life-areas"
              class="nav-link"
              :class="{ active: route.path === '/life-areas' }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
                <line x1="12" y1="22.08" x2="12" y2="12"></line>
              </svg>
              Области жизни
            </router-link>
            <router-link
              to="/projects"
              class="nav-link"
              :class="{ active: route.path === '/projects' || route.path.startsWith('/projects/') }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
              </svg>
              Проекты
            </router-link>
            <router-link
              to="/delos"
              class="nav-link"
              :class="{ active: route.path === '/delos' || route.path.startsWith('/delos/') }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 11l3 3L22 4"></path>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
              </svg>
              Дела
            </router-link>
            <router-link
              to="/goals"
              class="nav-link"
              :class="{ active: route.path === '/goals' || route.path.startsWith('/goals/') }"
            >
              Цели
            </router-link>
            <router-link
              to="/routines"
              class="nav-link"
              :class="{ active: route.path === '/routines' }"
            >
              Рутины
            </router-link>
            <router-link
              to="/import/xlsx"
              class="nav-link"
              :class="{ active: route.path === '/import/xlsx' }"
            >
              Импорт XLSX
            </router-link>
            <router-link
              to="/stats"
              class="nav-link"
              :class="{ active: route.path === '/stats' }"
            >
              Волна
            </router-link>
            <router-link to="/reports/checklist" class="nav-link" :class="{ active: route.path === '/reports/checklist' }">Отчёт чек-лист</router-link>
            <router-link
              to="/ideas"
              class="nav-link"
              :class="{ active: route.path === '/ideas' }"
            >
              Банк идей
            </router-link>
            <router-link
              to="/settings"
              class="nav-link"
              :class="{ active: route.path === '/settings' }"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="3"></circle>
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
              </svg>
              Настройки
            </router-link>
          </nav>

          <div class="user-menu">
            <span class="user-name">{{ username }}</span>
            <button @click="logout" class="btn btn-ghost" aria-label="Выйти" title="Выйти">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                <polyline points="16 17 21 12 16 7"></polyline>
                <line x1="21" y1="12" x2="9" y2="12"></line>
              </svg>
            </button>
          </div>
        </div>
      </header>

      <main class="app-main" role="main">
        <router-view />
      </main>

    </div>

    <div v-else class="app-shell">
      <router-view />
    </div>
  </div>
</template>