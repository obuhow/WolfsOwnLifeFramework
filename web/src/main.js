import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import LoginView from './components/LoginView.vue'
import SettingsView from './components/SettingsView.vue'
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

const routes = [
  { path: '/', redirect: '/today' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/today', component: TodayView, meta: { requiresAuth: true } },
  { path: '/morning', component: MorningView, meta: { requiresAuth: true } },
  { path: '/week', redirect: '/calendar' },
  { path: '/calendar', component: CalendarView, meta: { requiresAuth: true } },
  { path: '/gantt', redirect: '/planning' },
  { path: '/planning', component: PlanningView, meta: { requiresAuth: true } },
  { path: '/goals', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/goals/:id', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/ideas', component: IdeasView, meta: { requiresAuth: true } },
  { path: '/ideas/:id', component: IdeaDetailView, meta: { requiresAuth: true } },
  { path: '/settings', component: SettingsView, meta: { requiresAuth: true } },
  { path: '/life-areas', component: LifeAreasView, meta: { requiresAuth: true } },
  { path: '/projects', component: ProjectsView, meta: { requiresAuth: true } },
  { path: '/projects/:id', component: ProjectDetailView, meta: { requiresAuth: true } },
  { path: '/delos', component: DelosView, meta: { requiresAuth: true } },
  { path: '/delos/:id', component: DeloDetailView, meta: { requiresAuth: true } },
  { path: '/routines', component: RoutinesView, meta: { requiresAuth: true } },
  { path: '/import/xlsx', component: XlsxImportView, meta: { requiresAuth: true } },
  { path: '/stats', component: WaveStatsView, meta: { requiresAuth: true } },
  { path: '/reports/checklist', component: ChecklistReportView, meta: { requiresAuth: true } },
  { path: '/data-sync', component: DataSyncView, meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('wolf_token')
  if (to.meta.requiresAuth && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/today'
  }
})

createApp(App).use(router).mount('#app')
