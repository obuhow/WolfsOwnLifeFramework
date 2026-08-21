<script setup>
import { onMounted, onBeforeUnmount, ref, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { apiBase } from './api'

const router = useRouter()
const route = useRoute()
const token = ref(localStorage.getItem('wolf_token') || '')
const username = ref('')

// --- Navigation model (approved Release 0.3 IA) ---------------------------
const NAV = [
  { kind: 'link', label: 'Сегодня', to: '/today' },
  {
    kind: 'group', key: 'calendar', label: 'Календарь',
    children: [
      { label: 'Неделя', to: '/calendar' },
      { label: 'Месяц', to: '/calendar/month' },
    ],
  },
  {
    kind: 'group', key: 'planning', label: 'Планирование',
    children: [
      { label: 'Диаграмма Ганта', to: '/planning' },
      { label: 'Бэклог', to: '/planning/backlog' },
    ],
  },
  {
    kind: 'group', key: 'projects', label: 'Управление проектами',
    children: [
      { label: 'Области жизни', to: '/life-areas' },
      { label: 'Проекты', to: '/projects' },
      { label: 'Дела', to: '/delos' },
      { label: 'Банк идей', to: '/ideas' },
    ],
  },
  {
    kind: 'group', key: 'flow', label: 'Управление потоком',
    children: [
      { label: 'Цели', to: '/goals' },
      { label: 'Сферы жизни', to: '/spheres' },
      { label: 'Синергия', to: '/synergy' },
      { label: 'Утренний обход', to: '/morning' },
      { label: 'Заметки / LLM Wiki', to: '/notes' },
      { label: 'Отчёт «Чек-лист»', to: '/reports/checklist' },
    ],
  },
  { kind: 'link', label: 'Настройки', to: '/settings' },
]

// Prefix map for active-state detection (detail routes highlight their parent link).
function isChildActive(to) {
  const p = route.path
  if (p === to) return true
  // Detail routes share the list prefix
  const prefixes = ['/projects', '/delos', '/goals', '/ideas']
  if (prefixes.includes(to) && p.startsWith(to + '/')) return true
  return false
}

function isGroupActive(group) {
  return group.children.some((c) => isChildActive(c.to))
}

function groupKeyForPath() {
  for (const item of NAV) {
    if (item.kind === 'group' && isGroupActive(item)) return item.key
  }
  return null
}

// --- Desktop dropdown state ------------------------------------------------
const openGroup = ref(null) // key of the desktop group currently open

function toggleGroup(key) {
  openGroup.value = openGroup.value === key ? null : key
}
function closeGroups() {
  openGroup.value = null
}
function onGroupKeydown(e, key) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    toggleGroup(key)
  } else if (e.key === 'Escape') {
    closeGroups()
  }
}

// --- Mobile drawer state ---------------------------------------------------
const drawerOpen = ref(false)
const expandedGroups = ref({})
const drawerEl = ref(null)
const menuTriggerEl = ref(null)

function openDrawer() {
  drawerOpen.value = true
  // Current group starts expanded
  const active = groupKeyForPath()
  const state = {}
  for (const item of NAV) {
    if (item.kind === 'group') state[item.key] = item.key === active
  }
  expandedGroups.value = state
  nextTick(() => {
    document.body.style.overflow = 'hidden'
    const first = drawerEl.value?.querySelector('a, button')
    first?.focus()
  })
}

function closeDrawer() {
  drawerOpen.value = false
  document.body.style.overflow = ''
  nextTick(() => menuTriggerEl.value?.focus())
}

function toggleDrawerGroup(key) {
  expandedGroups.value = { ...expandedGroups.value, [key]: !expandedGroups.value[key] }
}

function onDrawerNavigate() {
  closeDrawer()
}

// Focus trap + Escape inside drawer
function onDrawerKeydown(e) {
  if (e.key === 'Escape') {
    e.preventDefault()
    closeDrawer()
    return
  }
  if (e.key !== 'Tab') return
  const focusables = drawerEl.value?.querySelectorAll(
    'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )
  if (!focusables || !focusables.length) return
  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}

// --- Global click-away / escape for desktop dropdowns ----------------------
function onDocClick(e) {
  if (!e.target.closest('.nav-group')) closeGroups()
}
function onDocKeydown(e) {
  if (e.key === 'Escape') closeGroups()
}

// Close menus on route change
watch(() => route.path, () => {
  token.value = localStorage.getItem('wolf_token') || ''
  closeGroups()
  if (drawerOpen.value) closeDrawer()
})

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
  if (drawerOpen.value) closeDrawer()
  router.push('/login')
}

onMounted(async () => {
  token.value = localStorage.getItem('wolf_token') || ''
  if (token.value) await loadUser()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <div id="app-root">
    <div v-if="token" class="app-shell">
      <header class="app-header">
        <div class="header-inner">
          <router-link to="/today" class="brand" aria-label="WOLF — Главная">
            <span class="brand-logo">WOLF</span>
          </router-link>

          <!-- Desktop top navigation -->
          <nav class="nav nav-desktop" aria-label="Основная навигация">
            <template v-for="item in NAV" :key="item.label">
              <router-link
                v-if="item.kind === 'link'"
                :to="item.to"
                class="nav-link"
                :class="{ active: isChildActive(item.to) }"
              >{{ item.label }}</router-link>

              <div
                v-else
                class="nav-group"
                :class="{ open: openGroup === item.key }"
              >
                <button
                  type="button"
                  class="nav-link nav-group-trigger"
                  :class="{ active: isGroupActive(item) }"
                  :aria-expanded="openGroup === item.key ? 'true' : 'false'"
                  :aria-controls="`grp-${item.key}`"
                  @click.stop="toggleGroup(item.key)"
                  @keydown="onGroupKeydown($event, item.key)"
                >
                  {{ item.label }}
                  <span class="caret" aria-hidden="true">▾</span>
                </button>
                <div
                  v-show="openGroup === item.key"
                  :id="`grp-${item.key}`"
                  class="nav-submenu"
                  role="menu"
                >
                  <router-link
                    v-for="child in item.children"
                    :key="child.to"
                    :to="child.to"
                    class="nav-submenu-link"
                    :class="{ active: isChildActive(child.to) }"
                    role="menuitem"
                    @click="closeGroups"
                  >{{ child.label }}</router-link>
                </div>
              </div>
            </template>
          </nav>

          <div class="header-right">
            <div class="user-menu">
              <span class="user-name">{{ username }}</span>
              <button @click="logout" class="btn btn-ghost logout-btn" aria-label="Выйти" title="Выйти">Выйти</button>
            </div>
            <button
              ref="menuTriggerEl"
              type="button"
              class="menu-trigger"
              aria-label="Меню"
              :aria-expanded="drawerOpen ? 'true' : 'false'"
              @click="openDrawer"
            >
              <span aria-hidden="true">☰</span>
            </button>
          </div>
        </div>
      </header>

      <!-- Mobile drawer -->
      <transition name="drawer">
        <div v-if="drawerOpen" class="drawer-overlay" @click="closeDrawer">
          <nav
            ref="drawerEl"
            class="drawer"
            aria-label="Мобильная навигация"
            @click.stop
            @keydown="onDrawerKeydown"
          >
            <div class="drawer-head">
              <span class="brand-logo">WOLF</span>
              <button type="button" class="menu-trigger" aria-label="Закрыть меню" @click="closeDrawer">
                <span aria-hidden="true">✕</span>
              </button>
            </div>

            <div class="drawer-body">
              <template v-for="item in NAV" :key="item.label">
                <router-link
                  v-if="item.kind === 'link'"
                  :to="item.to"
                  class="drawer-link"
                  :class="{ active: isChildActive(item.to) }"
                  @click="onDrawerNavigate"
                >{{ item.label }}</router-link>

                <div v-else class="drawer-group">
                  <button
                    type="button"
                    class="drawer-group-trigger"
                    :class="{ active: isGroupActive(item) }"
                    :aria-expanded="expandedGroups[item.key] ? 'true' : 'false'"
                    @click="toggleDrawerGroup(item.key)"
                  >
                    {{ item.label }}
                    <span class="caret" aria-hidden="true">{{ expandedGroups[item.key] ? '▾' : '▸' }}</span>
                  </button>
                  <div v-show="expandedGroups[item.key]" class="drawer-submenu">
                    <router-link
                      v-for="child in item.children"
                      :key="child.to"
                      :to="child.to"
                      class="drawer-submenu-link"
                      :class="{ active: isChildActive(child.to) }"
                      @click="onDrawerNavigate"
                    >{{ child.label }}</router-link>
                  </div>
                </div>
              </template>
            </div>

            <div class="drawer-foot">
              <span class="user-name">{{ username }}</span>
              <button type="button" class="btn btn-ghost" @click="logout">Выйти</button>
            </div>
          </nav>
        </div>
      </transition>

      <main class="app-main" role="main">
        <router-view />
      </main>
    </div>

    <div v-else class="app-shell">
      <router-view />
    </div>
  </div>
</template>
