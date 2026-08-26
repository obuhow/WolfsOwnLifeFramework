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
import { ref, onMounted } from 'vue'
import { apiBase } from '../api'
import ProfileChoiceList from './ProfileChoiceList.vue'

const settings = ref({
  timezone: 'Europe/Moscow',
  nightStart: '23:00',
  nightEnd: '07:00',
  dayEnd: '02:00',
  defaultSleepEnd: '09:00',
  hourAccountingMode: 'PRIMARY_ONLY',
  timeCaptureMode: 'PARALLEL_SLOTS',
  availableWeeklyHours: 30
})

const loading = ref(false)
const error = ref('')
const success = ref('')
const isAdmin = ref(false)

const timezones = [
  'Europe/Moscow',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/London',
  'America/New_York',
  'America/Los_Angeles',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Australia/Sydney',
  'UTC'
]

const hourAccountingModes = [
  { value: 'PRIMARY_ONLY', label: 'Только основной проект (по умолчанию)' },
  { value: 'ALL_PROJECTS', label: 'Все проекты' }
]

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.hash = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/settings`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    const data = await res.json()
    settings.value = {
      timezone: data.timezone,
      nightStart: data.nightStart?.slice(0, 5) || '23:00',
      nightEnd: data.nightEnd?.slice(0, 5) || '07:00',
      dayEnd: data.dayEnd?.slice(0, 5) || '02:00',
      defaultSleepEnd: data.defaultSleepEnd?.slice(0, 5) || '09:00',
      hourAccountingMode: data.hourAccountingMode,
      timeCaptureMode: data.timeCaptureMode || 'PARALLEL_SLOTS',
      availableWeeklyHours: data.availableWeeklyHours ?? 30
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.hash = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/settings`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(settings.value)
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    success.value = 'Настройки сохранены'
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/**
 * Повторная загрузка демо-профиля (релиз 0.6, тикет 05).
 *
 * Раскрывается по кнопке — тот же список карточек, что и на `/onboarding/profile`,
 * через общий `ProfileChoiceList.vue`. Выбор идёт на POST /onboarding/reload-profile,
 * который сначала полностью очищает данные профиля, затем наполняет заново. Мастер
 * первого входа при этом не запускается: пользователь уже прошёл Знакомство.
 */
const profilePickerOpen = ref(false)
const reloadingSlug = ref('')
const reloadError = ref('')
const reloadSuccess = ref('')

function toggleProfilePicker() {
  profilePickerOpen.value = !profilePickerOpen.value
  reloadError.value = ''
  reloadSuccess.value = ''
}

async function reloadProfile(slug) {
  if (reloadingSlug.value) return
  reloadError.value = ''
  reloadSuccess.value = ''
  reloadingSlug.value = slug
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.hash = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/onboarding/reload-profile`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ slug })
    })

    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }

    const data = await res.json()
    reloadSuccess.value = `Профиль «${data.displayName}» загружен`
    profilePickerOpen.value = false
    // Норма пришла из профиля — подтягиваем её в форму, чтобы поле не врало.
    await loadSettings()
  } catch (e) {
    reloadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    reloadingSlug.value = ''
  }
}

async function loadRole() {
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) return
    const res = await fetch(`${apiBase()}/auth/me`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!res.ok) return
    const data = await res.json()
    isAdmin.value = data.role === 'ADMIN'
  } catch {
    // ignore network blips — menu item just stays hidden
  }
}

onMounted(loadSettings)
onMounted(loadRole)
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <h1>Настройки</h1>
      <p class="eyebrow">Часовой пояс, конец дня, ночные часы, режим учёта</p>
    </header>

    <section class="card">
      <div v-if="loading" class="loading">Загрузка…</div>

      <div v-else class="settings-form">
        <fieldset class="settings-fieldset">
        <legend>Время и границы суток</legend>
        <div class="form-group">
          <label for="timezone">Часовой пояс</label>
          <select
            id="timezone"
            v-model="settings.timezone"
            :disabled="loading"
            class="input"
          >
            <option v-for="tz in timezones" :key="tz" :value="tz">{{ tz }}</option>
          </select>
          <p class="hint">Используется для границ дня и недели в календаре и Гантте</p>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="dayEnd">Конец дня</label>
            <input
              id="dayEnd"
              type="time"
              v-model="settings.dayEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Граница логических суток (например 02:00). Дела после полуночи до этого времени относятся к предыдущему календарному дню сетки.</p>
          </div>
          <div class="form-group">
            <label for="defaultSleepEnd">Конец авто-Сна</label>
            <input
              id="defaultSleepEnd"
              type="time"
              v-model="settings.defaultSleepEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Интервал «Сон» по умолчанию: от конца дня до этого времени (напр. 02:00–09:00)</p>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="nightStart">Начало ночных часов</label>
            <input
              id="nightStart"
              type="time"
              v-model="settings.nightStart"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Строки с этого времени скрываются по умолчанию</p>
          </div>

          <div class="form-group">
            <label for="nightEnd">Конец ночных часов</label>
            <input
              id="nightEnd"
              type="time"
              v-model="settings.nightEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">До этого времени строки скрыты (не включительно)</p>
          </div>
        </div>
        </fieldset>

        <fieldset class="settings-fieldset">
        <legend>Учёт часов и нагрузка</legend>
        <div class="form-group">
          <label for="hourAccountingMode">Режим учёта часов</label>
          <select
            id="hourAccountingMode"
            v-model="settings.hourAccountingMode"
            :disabled="loading"
            class="input"
          >
            <option v-for="mode in hourAccountingModes" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
          </select>
          <p class="hint">
            <strong>Только основной проект:</strong> часы засчитываются только в Основной проект Дела (по умолчанию).<br>
            <strong>Все проекты:</strong> часы засчитываются во все привязанные проекты Дела.
          </p>
        </div>

        <div class="form-group">
          <label for="timeCaptureMode">Режим фиксации времени</label>
          <select id="timeCaptureMode" v-model="settings.timeCaptureMode" :disabled="loading" class="input">
            <option value="PARALLEL_SLOTS">Параллельные слоты</option>
            <option value="PRIMARY_FOCUS">Фокус-сессия</option>
          </select>
          <p class="hint">Как удобнее вводить факт сегодня: вручную по 15-минутным слотам или одной Фокус-сессией с отметками переключений.</p>
        </div>

        <div class="form-group">
          <label for="availableWeeklyHours">Доступно часов в неделю</label>
          <input id="availableWeeklyHours" v-model.number="settings.availableWeeklyHours" type="number" min="0" step="0.25" :disabled="loading" class="input" />
          <p class="hint">Используется для нейтрального показа недельной нагрузки и влияния сдвига Проектов на Цели. По умолчанию — 30 ч.</p>
        </div>
        </fieldset>

        <div class="form-actions">
          <button
            @click="saveSettings"
            :disabled="loading"
            class="btn btn-primary"
          >
            <span v-if="loading">Сохранение…</span>
            <span v-else>Сохранить</span>
          </button>
        </div>

        <div v-if="error" class="alert alert-error">{{ error }}</div>
        <div v-if="success" class="alert alert-success">{{ success }}</div>
      </div>
    </section>

    <section class="card">
      <fieldset class="settings-fieldset">
        <legend>Демо-профиль</legend>
        <p class="hint">
          Загрузка другого профиля сначала удаляет всё, что создал текущий, — Проекты, Дела,
          Записи времени, Цели, Идеи и Заметки, включая добавленные вручную. Области жизни и
          9 Сфер остаются. Это замена профиля, а не слияние.
        </p>

        <button
          type="button"
          class="btn btn-ghost"
          data-demo-profile-toggle
          :disabled="!!reloadingSlug"
          @click="toggleProfilePicker"
        >
          {{ profilePickerOpen ? 'Скрыть выбор' : 'Загрузить другой профиль' }}
        </button>

        <ProfileChoiceList
          v-if="profilePickerOpen"
          class="profile-picker"
          :busy-slug="reloadingSlug"
          busy-label="Очищаю и загружаю…"
          @select="reloadProfile"
        />

        <div v-if="reloadError" class="alert alert-error">{{ reloadError }}</div>
        <div v-if="reloadSuccess" class="alert alert-success">{{ reloadSuccess }}</div>
      </fieldset>
    </section>

    <section v-if="isAdmin" class="card admin-section">
      <fieldset class="settings-fieldset">
        <legend>Администрирование</legend>
        <p class="hint">Выпуск и отзыв пригласительных кодов — доступно только администратору.</p>
        <router-link to="/admin/invites" class="btn btn-ghost">Пользователи / Инвайт-коды</router-link>
      </fieldset>
    </section>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 720px;
}

.settings-form {
  display: grid;
  gap: 1.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

.profile-picker {
  margin-top: 1rem;
}

@media (max-width: 560px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>