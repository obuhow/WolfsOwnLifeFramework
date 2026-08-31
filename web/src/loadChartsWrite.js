/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General License
 * along with this program. see <https://www.gnu.org/licenses/>.
 */

/**
 * Общий хелпер записи изменений для вкладок диаграмм нагрузки (release 0.8, тикет 02).
 *
 * Перетаскивание точки кривой и движение ползунка генерируют десятки промежуточных
 * значений. Запись уходит с дебаунсом 400 мс после завершения жеста (pointerup / change),
 * а не на каждое движение. Промежуточное состояние показывается локально сразу — интерфейс
 * не ждёт ответа сервера, но при ошибке PUT значение откатывается к серверному, а под
 * диаграммой появляется строка ошибки. Тихо расходиться с сервером диаграмма не может.
 *
 * Используется нативный fetch (см. web/src/api.js — токен в localStorage под ключом
 * wolf_token, но здесь полагаемся на готовый заголовок от authHeaders).
 */
import { ref } from 'vue'
import { authHeaders } from './api'

const DEBOUNCE_MS = 400

/**
 * @param {string} url            — полный URL запроса (apiBase() + путь)
 * @param {object} body           — тело PUT (будет сериализовано в JSON)
 * @param {object} [opts]
 * @param {string} [opts.localLabel] — человекочитаемая подпись жеста для строки ошибки
 * @returns {{ error: import('vue').Ref<string>, commit: (value?: object) => void, flush: () => void, cancel: () => void }}
 */
export function useLoadChartWrite(url, body, opts = {}) {
  const error = ref('')
  let timer = null
  let pending = null

  /**
   * Удаление точки кривой (DELETE /api/v1/projects/{id}/load-curve/{entryId}).
   * Тикет 03 расширяет контракт хелпера тикета 02: дебаунс неприменим к удалению —
   * оно уходит сразу. При ошибке вызывается opts.onError(snapshot), значение откатывается
   * к серверному (откат реализует вызывающая вкладка, подписанная на onError).
   *
   * @param {string} deleteUrl — полный URL удаляемого ресурса (без тела)
   * @param {object} [snapshot] — серверное значение для отката при ошибке
   */
  function remove(deleteUrl, snapshot) {
    const headers = authHeaders()
    if (!headers) return
    error.value = ''
    fetch(deleteUrl, { method: 'DELETE', headers })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
      })
      .catch((e) => {
        opts.onError?.(snapshot)
        error.value = `Не сохранено: удаление точки — ${e.message}`
      })
  }

  function sendNow() {
    const headers = authHeaders(true)
    if (!headers) return
    const snapshot = pending
    error.value = ''
    fetch(url, { method: 'PUT', headers, body: JSON.stringify(snapshot) })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
      })
      .catch((e) => {
        // Откат к серверному значению — вызывающая вкладка подписана на onError.
        opts.onError?.(snapshot)
        error.value = `Не сохранено: ${opts.localLabel || 'изменение'} — ${e.message}`
      })
    pending = null
  }

  function schedule() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(sendNow, DEBOUNCE_MS)
  }

  /**
   * Зафиксировать промежуточное значение и запланировать отложенную запись.
   * @param {object} value — значение, которое уйдёт в body PUT при срабатывании дебаунса.
   */
  function commit(value) {
    pending = value != null ? value : body
    schedule()
  }

  /** Принудительно слить отложенную запись (например, при размонтировании). */
  function flush() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    if (pending) sendNow()
  }

  function cancel() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    pending = null
  }

  return { error, commit, flush, cancel, remove }
}
