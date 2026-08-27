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
/**
 * Состояние тура Знакомства (релиз 0.6, тикет 03).
 *
 * Тур — не отдельный экран, а режим поверх обычной оболочки `App.vue`:
 * подсветка идёт по реальным пунктам верхнего уровня NAV, поэтому оболочка
 * должна быть отрисована, а маршрут — меняться как при обычной навигации.
 *
 * Прогресс по шагам живёт только в памяти вкладки. А вот факт завершения
 * знакомства — состояние пользователя, и он сохраняется на сервере
 * (POST /onboarding/complete): иначе гейт в `main.js` после тура снова уводил
 * бы на выбор демо-профиля, где повторная загрузка отклоняется, потому что
 * данные у пользователя уже есть.
 */
import { ref, readonly } from 'vue'
import { apiBase, authHeaders } from './api'

const active = ref(false)

// Тур запущен как часть первого входа, а не повторно из шапки. Только в этом
// случае по завершении показывается финальный выбор «Оставить»/«Очистить»:
// пользователю, который уже живёт в системе, предлагать очистку нельзя —
// кнопка «Очистить» удалит его настоящие данные.
let firstRun = false

// Кэш статуса онбординга общий с гейтом маршрутизатора: как только знакомство
// завершено, спрашивать сервер на каждом переходе незачем.
let completedCache = false

export const tourActive = readonly(active)

export function startTour(options = {}) {
  firstRun = options.firstRun === true
  active.value = true
}

export function isFirstRunTour() {
  return firstRun
}

export function endTour() {
  active.value = false
}

export function isTourActive() {
  return active.value
}

export function isOnboardingKnownCompleted() {
  return completedCache
}

export function markOnboardingCompleted() {
  completedCache = true
}

/**
 * Отмечает знакомство завершённым на сервере.
 *
 * Вызывается и при прохождении тура до конца, и при досрочном выходе: в обоих
 * случаях пользователь уже видел оболочку, и возвращать его в онбординг нельзя.
 * Сетевой сбой не запирает пользователя — кэш всё равно выставляется, дальше
 * решает ответ `/auth/me` при следующей загрузке страницы.
 *
 * @returns {Promise<boolean>} удалось ли записать статус на сервере
 */
export async function completeOnboarding() {
  markOnboardingCompleted()
  const headers = authHeaders()
  if (!headers) return false
  try {
    const res = await fetch(`${apiBase()}/onboarding/complete`, {
      method: 'POST',
      headers,
    })
    return res.ok
  } catch (e) {
    return false
  }
}
