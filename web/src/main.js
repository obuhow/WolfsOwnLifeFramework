import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './style.css'
import App from './App.vue'
import LoginView from './components/LoginView.vue'
import SettingsView from './components/SettingsView.vue'
import LifeAreasView from './components/LifeAreasView.vue'
import ProjectsView from './components/ProjectsView.vue'
import ProjectDetailView from './components/ProjectDetailView.vue'

const routes = [
  { path: '/', redirect: '/life-areas' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/settings', component: SettingsView, meta: { requiresAuth: true } },
  { path: '/life-areas', component: LifeAreasView, meta: { requiresAuth: true } },
  { path: '/projects', component: ProjectsView, meta: { requiresAuth: true } },
  { path: '/projects/:id', component: ProjectDetailView, meta: { requiresAuth: true } },
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
    return '/life-areas'
  }
})

createApp(App).use(router).mount('#app')
