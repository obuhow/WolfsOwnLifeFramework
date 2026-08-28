/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import LoginView from './components/LoginView.vue'
import RegisterView from './components/RegisterView.vue'
import ProfileLoadView from './components/ProfileLoadView.vue'
import OnboardingTourEntry from './components/OnboardingTourEntry.vue'
import OnboardingFinalChoice from './components/OnboardingFinalChoice.vue'
import OnboardingWizardView from './components/OnboardingWizardView.vue'
import { apiBase } from './api'
import { isTourActive, isOnboardingKnownCompleted, markOnboardingCompleted } from './onboardingTour'
import SettingsView from './components/SettingsView.vue'
import AdminInvitesView from './components/AdminInvitesView.vue'
import LifeAreasView from './components/LifeAreasView.vue'
import ProjectsView from './components/ProjectsView.vue'
import ProjectDetailView from './components/ProjectDetailView.vue'
import DelosView from './components/DelosView.vue'
import DeloDetailView from './components/DeloDetailView.vue'
import TodayView from './components/TodayView.vue'
import WeekView from './components/WeekView.vue'
import CalendarView from './components/CalendarView.vue'
import GanttView from './components/GanttView.vue'
import PlanningView from './components/PlanningView.vue'
import GoalsView from './components/GoalsView.vue'
import IdeasView from './components/IdeasView.vue'
import IdeaDetailView from './components/IdeaDetailView.vue'
import MorningView from './components/MorningView.vue'
import RoutinesView from './components/RoutinesView.vue'
import XlsxImportView from './components/XlsxImportView.vue'
import WaveStatsView from './components/WaveStatsView.vue'
import ChecklistReportView from './components/ChecklistReportView.vue'
import DataSyncView from './components/DataSyncView.vue'
import BacklogView from './components/BacklogView.vue'
import ImportChatPanel from './components/ImportChatPanel.vue'
import SpheresView from './components/SpheresView.vue'
import PlaceholderView from './components/PlaceholderView.vue'
import DocsIndexView from './components/docs/DocsIndexView.vue'
import ManifestoView from './components/docs/ManifestoView.vue'
import ScenariosView from './components/docs/ScenariosView.vue'
import SelfHostingView from './components/docs/SelfHostingView.vue'

const routes = [
  { path: '/', redirect: '/today' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/register', component: RegisterView, meta: { public: true } },
  // Онбординг: выбор демо-профиля (релиз 0.6, тикет 02). Тур — тикет 03.
  { path: '/onboarding/profile', component: ProfileLoadView, meta: { requiresAuth: true } },
  // Тур Знакомства (тикет 03): маршрут только включает режим тура и уходит на
  // первый его пункт — сам тур идёт поверх обычной оболочки.
  { path: '/onboarding/tour', component: OnboardingTourEntry, meta: { requiresAuth: true } },
  // Финальный выбор «Оставить / Очистить» (тикет 04).
  { path: '/onboarding/final', component: OnboardingFinalChoice, meta: { requiresAuth: true } },
  // Мастер первого входа Проект → Цель → Норма (0.4-08). Получает управление по
  // ветке «Очистить профиль» Финального выбора; экран написан в тикете 04, API — в 0.4.
  { path: '/onboarding', component: OnboardingWizardView, meta: { requiresAuth: true } },
  // Утренний обход
  { path: '/morning', component: MorningView, meta: { requiresAuth: true } },
  // Ежедневник: Неделя (осн.), Сегодня и Месяц — вкладки/deep links той же страницы
  { path: '/week', redirect: '/calendar' },
  { path: '/calendar', component: CalendarView, meta: { requiresAuth: true } },
  { path: '/calendar/month', redirect: { path: '/calendar', query: { view: 'month' } } },
  { path: '/today', component: TodayView, meta: { requiresAuth: true } },
  // Управление делами · Планирование
  { path: '/gantt', redirect: '/roadmap' },
  { path: '/planning', redirect: '/roadmap' },
  { path: '/roadmap', component: PlanningView, meta: { requiresAuth: true } },
  { path: '/backlog', component: BacklogView, meta: { requiresAuth: true } },
  { path: '/planning/backlog', redirect: '/backlog' },
  // Управление делами · Сущности
  { path: '/projects', component: ProjectsView, meta: { requiresAuth: true } },
  { path: '/projects/:id', component: ProjectDetailView, meta: { requiresAuth: true } },
  { path: '/routines', component: RoutinesView, meta: { requiresAuth: true } },
  { path: '/delos', component: DelosView, meta: { requiresAuth: true } },
  { path: '/delos/:id', component: DeloDetailView, meta: { requiresAuth: true } },
  { path: '/ideas', component: IdeasView, meta: { requiresAuth: true } },
  { path: '/ideas/:id', component: IdeaDetailView, meta: { requiresAuth: true } },
  // Управление делами · Аналитика
  { path: '/stats', component: WaveStatsView, meta: { requiresAuth: true } },
  { path: '/checklist', component: ChecklistReportView, meta: { requiresAuth: true } },
  { path: '/reports/checklist', redirect: '/checklist' },
  // Управление потоком
  { path: '/life-areas', component: LifeAreasView, meta: { requiresAuth: true } },
  { path: '/goals', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/goals/:id', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/spheres', redirect: '/competency' },
  { path: '/synergy', redirect: '/competency' },
  { path: '/competency', component: SpheresView, meta: { requiresAuth: true } },
  { path: '/notes', redirect: '/knowledge' },
  { path: '/knowledge', component: PlaceholderView, meta: { requiresAuth: true, title: 'Личная база знаний', note: 'Управление потоком · заметки, источники и материалы агента', owner: '09', sections: [
    { title: 'Поиск и фильтры', description: 'Фактические фильтры над хронологическим реестром заметок.', fields: ['Поиск по тексту', 'Фильтр по источнику и автору', 'Фильтр по тегам'], empty: 'Фильтры подключаются в тикете 09.' },
    { title: 'Хронологический реестр', description: 'Каждая заметка показывает контекст источника, автора, отметку времени, текст или расшифровку и ручные теги. Материал агента помечен подписью автора и тонким левым правилом, а не ярким блоком.', fields: ['Контекст источника и автор', 'Отметка времени', 'Текст или расшифровка', 'Ручные теги', 'Аудио — явная строка вложения'], empty: 'Заметки подключаются в тикете 09.' },
    { title: 'Сводка', description: 'Запрос сводки — явное текстовое действие, ответ — помеченная секция, не чат-пузырь.', empty: 'Сводка подключается в тикете 09.' }
  ] } },
  // Настройки (в т.ч. Импорт XLSX + Синхронизация)
  { path: '/settings', component: SettingsView, meta: { requiresAuth: true } },
  { path: '/admin/invites', component: AdminInvitesView, meta: { requiresAuth: true } },
  { path: '/import/xlsx', component: XlsxImportView, meta: { requiresAuth: true } },
  { path: '/data-sync', component: DataSyncView, meta: { requiresAuth: true } },
  // Релиз 0.7, тикет 02: канал чат-панели импорта записей (плавающая панель в
  // App.vue, маршрут — для прямой ссылки / открытия в основной области).
  { path: '/import/chat', component: ImportChatPanel, meta: { requiresAuth: true } },
  // Документация — доступна без аутентификации (см. тикет 02 релиза 0.5)
  { path: '/docs', component: DocsIndexView, meta: { public: true } },
  { path: '/docs/manifesto', component: ManifestoView, meta: { public: true } },
  { path: '/docs/scenarios', component: ScenariosView, meta: { public: true } },
  { path: '/docs/self-hosting', component: SelfHostingView, meta: { public: true } },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    }
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

// Статус онбординга кэшируется в `onboardingTour.js` — общий кэш с туром,
// чтобы завершение знакомства сразу открывало оболочку без лишнего запроса.
async function isOnboardingCompleted(token) {
  if (isOnboardingKnownCompleted()) return true
  try {
    const res = await fetch(`${apiBase()}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (res.ok) {
      const data = await res.json()
      if (data.onboardingCompleted) {
        markOnboardingCompleted()
        return true
      }
      return false
    }
  } catch (e) {
    // Сетевой сбой — не запираем пользователя в онбординге.
  }
  return true
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('wolf_token')
  if (to.meta.requiresAuth && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/today'
  }
  // Онбординг-гейт (релиз 0.6, тикет 02): аутентифицирован + онбординг не завершён
  // + маршрут не /onboarding → на экран выбора демо-профиля.
  // Тур Знакомства (тикет 03) идёт по обычным маршрутам оболочки при ещё не
  // завершённом онбординге — пока он активен, гейт молчит, иначе первый же шаг
  // тура вернул бы гостя на экран выбора профиля.
  if (token && to.meta.requiresAuth && !to.path.startsWith('/onboarding') && !isTourActive()) {
    const completed = await isOnboardingCompleted(token)
    if (!completed) {
      return '/onboarding/profile'
    }
  }
})

createApp(App).use(router).mount('#app')
