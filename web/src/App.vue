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
import { onMounted, onBeforeUnmount, ref, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { apiBase } from './api'
import { tourActive, startTour } from './onboardingTour'
import OnboardingTour from './components/OnboardingTour.vue'
import ImportChatPanel from './components/ImportChatPanel.vue'

const router = useRouter()
const route = useRoute()
const token = ref(localStorage.getItem('wolf_token') || '')
const username = ref('')
const isAdmin = ref(false)

// Версия сборки (релиз 0.6, тикет 08). `__APP_VERSION__` — compile-time
// константа из web/vite.config.js, читающая web/package.json. Смена версии
// в package.json отражается в шапке после пересборки, без правок App.vue.
const appVersion = __APP_VERSION__

// Онбординг-маршруты (релиз 0.6) — полноэкранные, без навигационной оболочки.
const isOnboarding = computed(() => route.path.startsWith('/onboarding'))

// --- Navigation model (Release 0.5 IA — see wolf-life-os skill reference
// navigation-ia-05-supersession.md and .scratch/wayfinder-releases-05-07/issues/04) ---
// `tour` — стабильный якорь для тура Знакомства (релиз 0.6, тикет 03). Тур
// только читает эти атрибуты через `data-tour-target`; структуру NAV не меняет.
const NAV = [
  { kind: 'link', label: 'Утренний обход', to: '/morning', tour: 'morning' },
  { kind: 'link', label: 'Ежедневник', to: '/calendar', tour: 'calendar' },
  {
    kind: 'group', key: 'delo-management', label: 'Управление делами', tour: 'delo-management',
    subgroups: [
      {
        title: 'Планирование',
        children: [
          { label: 'Дорожная карта', to: '/roadmap' },
          { label: 'План нагрузки', to: '/load-plan' },
          { label: 'Бэклог', to: '/backlog' },
        ],
      },
      {
        title: 'Сущности',
        children: [
          { label: 'Проекты', to: '/projects' },
          { label: 'Рутины', to: '/routines' },
          { label: 'Дела', to: '/delos' },
          { label: 'Банк идей', to: '/ideas' },
        ],
      },
      {
        title: 'Аналитика',
        children: [
          { label: 'Статистика', to: '/stats' },
          { label: 'Чек-лист', to: '/checklist' },
        ],
      },
    ],
  },
  {
    kind: 'group', key: 'flow', label: 'Управление потоком', tour: 'flow',
    children: [
      { label: 'Области жизни', to: '/life-areas' },
      { label: 'Цели', to: '/goals' },
      { label: 'Диаграмма компетенций', to: '/competency' },
      { label: 'Личная база знаний', to: '/knowledge' },
    ],
  },
  {
    kind: 'group', key: 'docs', label: 'Документация', tour: 'docs',
    children: [
      { label: 'Манифест WOLF', to: '/docs/manifesto' },
      { label: 'Сценарии использования', to: '/docs/scenarios' },
      { label: 'Технические инструкции', to: '/docs/self-hosting' },
    ],
  },
  { kind: 'link', label: 'Администрирование', to: '/admin/users', adminOnly: true },
  { kind: 'action', label: 'Приветственный тур', action: 'tour', tour: 'intro' },
  {
    kind: 'group', key: 'settings', label: 'Настройки', tour: 'settings',
    children: [
      { label: 'Настройки', to: '/settings' },
      { label: 'Импорт XLSX', to: '/import/xlsx' },
      { label: 'Синхронизация данных', to: '/data-sync' },
    ],
  },
]

// Flat list of every {label, to} pair, group children and subgroup children included.
function flatChildren(item) {
  if (item.kind === 'link') return []
  if (item.children) return item.children
  if (item.subgroups) return item.subgroups.flatMap((sg) => sg.children)
  return []
}

// Anchor-aware match: '/roadmap#load-plan' is active whenever the path is /roadmap.
function baseOf(to) {
  const hashIdx = to.indexOf('#')
  return hashIdx === -1 ? to : to.slice(0, hashIdx)
}

// Prefix map for active-state detection (detail routes highlight their parent link).
function isChildActive(to) {
  const p = route.path
  const base = baseOf(to)
  if (p === base) return true
  // Detail routes share the list prefix
  const prefixes = ['/projects', '/delos', '/goals', '/ideas']
  if (prefixes.includes(base) && p.startsWith(base + '/')) return true
  return false
}

function isGroupActive(group) {
  return flatChildren(group).some((c) => isChildActive(c.to))
}

// Пункт-действие NAV (напр. «Приветственный тур»): повторный запуск тура из
// шапки — без firstRun, чтобы не показать существующему пользователю финальный
// выбор «Очистить» (который удалил бы его реальные данные). Поведение совпадает
// со старой отдельной кнопкой тура.
function runNavAction(action) {
  if (action === 'tour') {
    startTour()
    router.push('/morning')
  }
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

// --- Chat-panel import (release 0.7, ticket 02) ----------------------------
const importOpen = ref(false)
function toggleImport() { importOpen.value = !importOpen.value }
function closeImport() { importOpen.value = false }

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
      isAdmin.value = false
      router.push('/login')
      return
    }
    if (res.ok) {
      const data = await res.json()
      username.value = data.username || 'admin'
      isAdmin.value = data.role === 'ADMIN'
    }
  } catch (e) {
    // ignore network blips
  }
}

async function logout() {
  localStorage.removeItem('wolf_token')
  token.value = ''
  username.value = ''
  isAdmin.value = false
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
    <div v-if="token && !isOnboarding" class="app-shell">
      <header class="app-header">
        <div class="header-inner">
          <router-link to="/morning" class="brand" aria-label="WOLF — Главная">
            <div class="brand-container">
              <div class="brand-logo">WOLF</div>
              <div class="brand-tagline">Система управления потоком</div>
              <div class="brand-version">v{{ appVersion }}</div>
            </div>
          </router-link>

          <!-- Desktop top navigation -->
          <nav class="nav nav-desktop" aria-label="Основная навигация">
            <template v-for="item in NAV" :key="item.label">
              <template v-if="!item.adminOnly || isAdmin">
              <router-link
                v-if="item.kind === 'link'"
                :to="item.to"
                class="nav-link"
                :data-tour-target="item.tour"
                :class="{ active: isChildActive(item.to) }"
              >{{ item.label }}</router-link>

              <button
                v-else-if="item.kind === 'action'"
                type="button"
                class="nav-link nav-action"
                :data-tour-target="item.tour"
                @click="runNavAction(item.action)"
              >{{ item.label }}</button>

              <div
                v-else
                class="nav-group"
                :class="{ open: openGroup === item.key }"
              >
                <button
                  type="button"
                  class="nav-link nav-group-trigger"
                  :data-tour-target="item.tour"
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
                  :class="{ 'nav-submenu-grouped': !!item.subgroups }"
                  role="menu"
                >
                  <template v-if="item.subgroups">
                    <div v-for="subgroup in item.subgroups" :key="subgroup.title" class="nav-subgroup">
                      <div class="nav-subgroup-title">{{ subgroup.title }}</div>
                      <router-link
                        v-for="child in subgroup.children"
                        :key="child.to"
                        :to="child.to"
                        class="nav-submenu-link"
                        :class="{ active: isChildActive(child.to) }"
                        role="menuitem"
                        @click="closeGroups"
                      >{{ child.label }}</router-link>
                    </div>
                  </template>
                  <template v-else>
                    <router-link
                      v-for="child in item.children"
                      :key="child.to"
                      :to="child.to"
                      class="nav-submenu-link"
                      :class="{ active: isChildActive(child.to) }"
                      role="menuitem"
                      @click="closeGroups"
                    >{{ child.label }}</router-link>
                  </template>
                </div>
              </div>
              </template>
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
              data-tour-target="menu"
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
              <div class="brand-container-sm">
                <div class="brand-logo-sm">WOLF</div>
                <div class="brand-tagline-sm">Система управления потоком</div>
                <div class="brand-version-sm">v{{ appVersion }}</div>
              </div>
              <button type="button" class="menu-trigger" aria-label="Закрыть меню" @click="closeDrawer">
                <span aria-hidden="true">✕</span>
              </button>
            </div>

            <div class="drawer-body">
              <template v-for="item in NAV" :key="item.label">
                <template v-if="!item.adminOnly || isAdmin">
                <router-link
                  v-if="item.kind === 'link'"
                  :to="item.to"
                  class="drawer-link"
                  :data-tour-target="item.tour"
                  :class="{ active: isChildActive(item.to) }"
                  @click="onDrawerNavigate"
                >{{ item.label }}</router-link>

                <button
                  v-else-if="item.kind === 'action'"
                  type="button"
                  class="drawer-link drawer-action"
                  :data-tour-target="item.tour"
                  @click="() => { runNavAction(item.action); closeDrawer(); }"
                >{{ item.label }}</button>

                <div v-else class="drawer-group">
                  <button
                    type="button"
                    class="drawer-group-trigger"
                    :data-tour-target="item.tour"
                    :class="{ active: isGroupActive(item) }"
                    :aria-expanded="expandedGroups[item.key] ? 'true' : 'false'"
                    @click="toggleDrawerGroup(item.key)"
                  >
                    {{ item.label }}
                    <span class="caret" aria-hidden="true">{{ expandedGroups[item.key] ? '▾' : '▸' }}</span>
                  </button>
                  <div v-show="expandedGroups[item.key]" class="drawer-submenu">
                    <template v-if="item.subgroups">
                      <div v-for="subgroup in item.subgroups" :key="subgroup.title" class="drawer-subgroup">
                        <div class="drawer-subgroup-title">{{ subgroup.title }}</div>
                        <router-link
                          v-for="child in subgroup.children"
                          :key="child.to"
                          :to="child.to"
                          class="drawer-submenu-link"
                          :class="{ active: isChildActive(child.to) }"
                          @click="onDrawerNavigate"
                        >{{ child.label }}</router-link>
                      </div>
                    </template>
                    <template v-else>
                      <router-link
                        v-for="child in item.children"
                        :key="child.to"
                        :to="child.to"
                        class="drawer-submenu-link"
                        :class="{ active: isChildActive(child.to) }"
                        @click="onDrawerNavigate"
                      >{{ child.label }}</router-link>
                    </template>
                  </div>
                </div>
                </template>
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

      <!-- Чат-панель импорта записей (релиз 0.7, тикет 02): плавающая кнопка
           открывает карточку предпросмотра поверх основного контента. -->
      <button
        v-if="token && !isOnboarding"
        type="button"
        class="import-fab"
        :class="{ active: importOpen }"
        :aria-expanded="importOpen ? 'true' : 'false'"
        aria-label="Импорт записей"
        title="Импорт записей"
        @click="toggleImport"
      >
        <span aria-hidden="true">＋</span>
      </button>

      <transition name="import-slide">
        <aside
          v-if="token && !isOnboarding && importOpen"
          class="import-dock"
          aria-label="Карточка импорта записей"
        >
          <button type="button" class="import-dock-close" aria-label="Закрыть" @click="closeImport">✕</button>
          <ImportChatPanel />
        </aside>
      </transition>

      <!-- Тур Знакомства (релиз 0.6, тикет 03) — поверх реальной оболочки,
           потому что подсвечивает настоящие пункты NAV, а не их копии. -->
      <OnboardingTour v-if="tourActive" />
    </div>

    <div v-else class="app-shell">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
/* Чат-панель импорта записей (релиз 0.7, тикет 02) — плавающая кнопка + док.
   «Тихий» вид: без ярких рамок, цвета из темы интерфейса. */
.import-fab {
  position: fixed;
  right: 1.4rem;
  bottom: 1.4rem;
  z-index: 40;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  border: 1px solid var(--border, #e6dfd4);
  background: var(--card, #fffdf9);
  color: var(--accent, #9a7b4f);
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
}
.import-fab:hover, .import-fab.active { background: var(--accent, #9a7b4f); color: #fff; }

.import-dock {
  position: fixed;
  right: 1.4rem;
  bottom: 4.6rem;
  z-index: 41;
  max-height: calc(100vh - 6rem);
  overflow: auto;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.16);
}
.import-dock-close {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  z-index: 2;
  border: none;
  background: transparent;
  color: var(--muted-foreground, #756d64);
  font-size: 1rem;
  cursor: pointer;
  line-height: 1;
}

.import-slide-enter-active, .import-slide-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.import-slide-enter-from, .import-slide-leave-to { opacity: 0; transform: translateY(8px); }
</style>

