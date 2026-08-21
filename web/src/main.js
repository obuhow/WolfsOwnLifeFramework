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
import PlaceholderView from './components/PlaceholderView.vue'

const routes = [
  { path: '/', redirect: '/today' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/today', component: TodayView, meta: { requiresAuth: true } },
  { path: '/morning', component: MorningView, meta: { requiresAuth: true } },
  // Календарь
  { path: '/week', redirect: '/calendar' },
  { path: '/calendar', component: CalendarView, meta: { requiresAuth: true } },
  { path: '/calendar/month', component: PlaceholderView, meta: { requiresAuth: true, title: 'Месяц', note: 'Календарь · спокойная сетка месяца', owner: '08', sections: [
    { title: 'Сетка месяца', description: 'Семь колонок «Пн–Вс», номер дня и компактные фактические подписи записей. Не является редактором слотов — постановка времени остаётся в «Неделе» и «Сегодня».', fields: ['Номер дня и признак «вне месяца»', 'Компактные подписи записей времени', 'Переход к дню открывает Неделю'], empty: 'Сетка появится вместе с месячным контрактом (тикет 08).' }
  ], asides: [
    { title: 'Бэклог недели', description: 'Сгруппированный по Проектам реестр с «x / y ч».', empty: 'Данные подключаются в тикете 08.' },
    { title: 'Бэклог месяца', description: 'Тот же групповой реестр в месячном периоде.', empty: 'Данные подключаются в тикете 08.' }
  ] } },
  // Планирование
  { path: '/gantt', redirect: '/planning' },
  { path: '/planning', component: PlanningView, meta: { requiresAuth: true } },
  { path: '/planning/backlog', component: PlaceholderView, meta: { requiresAuth: true, title: 'Бэклог', note: 'Планирование · полностраничный бэклог периода', owner: '08', sections: [
    { title: 'Период', description: 'Переключатель охвата «Неделя» / «Месяц» и навигация по периодам.', fields: ['Охват: Неделя / Месяц', 'Навигация по периодам', 'Текущий период как метаданные'], empty: 'Контролы периода подключаются в тикете 08.' },
    { title: 'Группы по Проектам', description: 'Тот же групповой реестр, что в «Сегодня» и «Неделе»: заголовок Проекта и «x / y ч», Дела без Проекта — в группе «Без проекта».', fields: ['Заголовок Проекта и «x / y ч»', 'Дела периода внутри группы', 'Явное текстовое действие «Перенести в неделю»'], empty: 'Группы появятся в тикете 08.' }
  ] } },
  // Управление проектами
  { path: '/life-areas', component: LifeAreasView, meta: { requiresAuth: true } },
  { path: '/projects', component: ProjectsView, meta: { requiresAuth: true } },
  { path: '/projects/:id', component: ProjectDetailView, meta: { requiresAuth: true } },
  { path: '/delos', component: DelosView, meta: { requiresAuth: true } },
  { path: '/delos/:id', component: DeloDetailView, meta: { requiresAuth: true } },
  { path: '/ideas', component: IdeasView, meta: { requiresAuth: true } },
  { path: '/ideas/:id', component: IdeaDetailView, meta: { requiresAuth: true } },
  // Управление потоком
  { path: '/goals', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/goals/:id', component: GoalsView, meta: { requiresAuth: true } },
  { path: '/spheres', component: PlaceholderView, meta: { requiresAuth: true, title: 'Сферы жизни', note: 'Управление потоком · справочник сфер', owner: '09', sections: [
    { title: 'Справочник сфер', description: 'Реестр с правилами: название и краткое описание сферы. Девять сфер — справочные данные, а не оценка пользователя.', fields: ['Название сферы', 'Краткое описание'], empty: 'Список подключается в тикете 09.' }
  ] } },
  { path: '/synergy', component: PlaceholderView, meta: { requiresAuth: true, title: 'Синергия', note: 'Управление потоком · влияние на сферы жизни', owner: '09', sections: [
    { title: 'Матрица синергии', description: 'Субъект (Проект или Идея), Сфера жизни, знак влияния и описание. Знак и текст — без красного кодирования отрицательного влияния.', fields: ['Субъект: Проект или Идея', 'Сфера жизни', 'Влияние: +, 0, −', 'Описание влияния'], empty: 'Матрица подключается в тикете 09.' }
  ] } },
  { path: '/notes', component: PlaceholderView, meta: { requiresAuth: true, title: 'Заметки / LLM Wiki', note: 'Управление потоком · заметки, источники и материалы агента', owner: '09', sections: [
    { title: 'Поиск и фильтры', description: 'Фактические фильтры над хронологическим реестром заметок.', fields: ['Поиск по тексту', 'Фильтр по источнику и автору', 'Фильтр по тегам'], empty: 'Фильтры подключаются в тикете 09.' },
    { title: 'Хронологический реестр', description: 'Каждая заметка показывает контекст источника, автора, отметку времени, текст или расшифровку и ручные теги. Материал агента помечен подписью автора и тонким левым правилом, а не ярким блоком.', fields: ['Контекст источника и автор', 'Отметка времени', 'Текст или расшифровка', 'Ручные теги', 'Аудио — явная строка вложения'], empty: 'Заметки подключаются в тикете 09.' },
    { title: 'Сводка', description: 'Запрос сводки — явное текстовое действие, ответ — помеченная секция, не чат-пузырь.', empty: 'Сводка подключается в тикете 09.' }
  ] } },
  { path: '/reports/checklist', component: ChecklistReportView, meta: { requiresAuth: true } },
  // Настройки
  { path: '/settings', component: SettingsView, meta: { requiresAuth: true } },
  // Совместимость: маршруты без прямого пункта в согласованной навигации
  { path: '/routines', component: RoutinesView, meta: { requiresAuth: true } },
  { path: '/import/xlsx', component: XlsxImportView, meta: { requiresAuth: true } },
  { path: '/stats', component: WaveStatsView, meta: { requiresAuth: true } },
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
