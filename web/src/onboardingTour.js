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
 * Состояние Приветственного тура (релиз 0.6, тикет 03; переработка — релиз 1.2,
 * тикет 01).
 *
 * Тур — не отдельный экран, а режим поверх обычной оболочки `App.vue`:
 * подсветка идёт по реальным пунктам верхнего уровня NAV, поэтому оболочка
 * должна быть отрисована, а маршрут — меняться как при обычной навигации.
 *
 * Факт завершения знакомства — состояние пользователя, оно сохраняется на
 * сервере (POST /onboarding/complete): иначе гейт в `main.js` после тура снова
 * уводил бы на выбор демо-профиля, где повторная загрузка отклоняется, потому
 * что данные у пользователя уже есть.
 *
 * Прогресс по шагам (индекс шага и признак активности) с релиза 1.2 хранится в
 * `sessionStorage`, а не только в памяти вкладки: сквозной сценарий из 15 шагов
 * с созданием сущностей длиннее прежних шести, и перезагрузка посреди тура
 * иначе отбрасывала бы гостя на шаг 1, притом что созданные им проект/дело/
 * записи остаются — рассинхрон текста тура с состоянием данных. Решение 0.6-03
 * п.4 («состояние тура НЕ сохраняется в БД») остаётся в силе — меняется только
 * носитель клиентского состояния (память вкладки → sessionStorage).
 */
import { ref, readonly } from 'vue'
import { apiBase, authHeaders } from './api'

const STORAGE_ACTIVE = 'wolf.tour.active'
const STORAGE_STEP = 'wolf.tour.step'
const STORAGE_FIRSTRUN = 'wolf.tour.firstRun'

function readStorage(key) {
  try {
    return window.sessionStorage.getItem(key)
  } catch (e) {
    return null
  }
}

function writeStorage(key, value) {
  try {
    if (value === null || value === undefined) window.sessionStorage.removeItem(key)
    else window.sessionStorage.setItem(key, String(value))
  } catch (e) {
    /* приватный режим / отключённое хранилище — тур работает в памяти вкладки */
  }
}

// Восстанавливаем активность и firstRun из sessionStorage при загрузке модуля,
// чтобы перезагрузка посреди тура не сбрасывала режим.
const active = ref(readStorage(STORAGE_ACTIVE) === '1')

// Тур запущен как часть первого входа, а не повторно из шапки. Только в этом
// случае по завершении показывается финальный выбор «Оставить»/«Очистить»:
// пользователю, который уже живёт в системе, предлагать очистку нельзя —
// кнопка «Очистить» удалит его настоящие данные.
let firstRun = readStorage(STORAGE_FIRSTRUN) === '1'

// Кэш статуса онбординга общий с гейтом маршрутизатора: как только знакомство
// завершено, спрашивать сервер на каждом переходе незачем.
let completedCache = false

export const tourActive = readonly(active)

export function startTour(options = {}) {
  firstRun = options.firstRun === true
  active.value = true
  writeStorage(STORAGE_ACTIVE, '1')
  writeStorage(STORAGE_FIRSTRUN, firstRun ? '1' : '0')
  // Новый запуск всегда с первого шага; движок перезапишет индекс при advance.
  writeStorage(STORAGE_STEP, '0')
}

export function isFirstRunTour() {
  return firstRun
}

export function endTour() {
  active.value = false
  writeStorage(STORAGE_ACTIVE, null)
  writeStorage(STORAGE_STEP, null)
  writeStorage(STORAGE_FIRSTRUN, null)
}

export function isTourActive() {
  return active.value
}

/**
 * Сохранённый индекс шага (0-based) для восстановления после перезагрузки.
 * Возвращает 0, если ничего не сохранено или значение невалидно.
 */
export function loadStepIndex() {
  const raw = readStorage(STORAGE_STEP)
  const n = Number.parseInt(raw ?? '', 10)
  return Number.isFinite(n) && n >= 0 ? n : 0
}

/** Сохраняет текущий индекс шага, чтобы F5 посреди тура не сбрасывал прогресс. */
export function saveStepIndex(index) {
  writeStorage(STORAGE_STEP, index)
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
