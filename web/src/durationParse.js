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
 * Разбор человеческого формата длительности (релиз 1.2, тикет 07).
 *
 * Форма ввода длительности при клике по свободному слоту Ежедневника: запись
 * создаётся на N последовательных 15-минутных слотов. Возврат отменённой формы —
 * решение владельца реш. C (grill 1.2); нужна шагу 11 Приветственного тура.
 */

export const SLOT_MINUTES = 15

/**
 * Разбирает строку длительности в минуты, округляя ВВЕРХ до кратности 15 (шаг
 * сетки WOLF), но не меньше 15. Пустая или непонятная строка → 15 (прежнее
 * поведение «один слот», чтобы простое подтверждение выбора Дела не менялось).
 *
 * Понимает: «1 ч 30 м», «1ч30м», «1.5 ч», «1,5 ч», «90 м», «90 минут», «90 мин»,
 * «1:30», голое число «90» (как минуты).
 *
 * @param {string} raw
 * @returns {number} минуты, кратные 15, ≥ 15
 */
export function parseDuration(raw) {
  const s = String(raw || '').trim().toLowerCase().replace(',', '.')
  if (!s) return SLOT_MINUTES

  let minutes = null

  // «1:30» — часы:минуты
  const colon = s.match(/^(\d+)\s*:\s*(\d{1,2})$/)
  if (colon) {
    minutes = Number(colon[1]) * 60 + Number(colon[2])
  }

  if (minutes === null) {
    // Часы и/или минуты словами/буквами: «1 ч 30 м», «1.5 ч», «90 мин», «90 м».
    // Не используем \b после кириллицы — в JS \b определён для ASCII \w, а «ч»/«м»
    // им не являются, поэтому граница слова после кириллицы ненадёжна. Вместо неё
    // требуем, чтобы за единицей шёл не-буквенный символ или конец строки.
    const hoursMatch = s.match(/(\d+(?:\.\d+)?)\s*(?:ч|час(?:а|ов)?|hours?|h)(?![a-zа-я])/)
    const minsMatch = s.match(/(\d+)\s*(?:м|мин|минут[аы]?|minutes?|min|m)(?![a-zа-я])/)
    if (hoursMatch || minsMatch) {
      const h = hoursMatch ? Number(hoursMatch[1]) : 0
      const m = minsMatch ? Number(minsMatch[1]) : 0
      minutes = Math.round(h * 60) + m
    }
  }

  if (minutes === null) {
    // Голое число — трактуем как минуты («90» → 90 мин).
    const bare = s.match(/^(\d+(?:\.\d+)?)$/)
    if (bare) minutes = Math.round(Number(bare[1]))
  }

  if (minutes === null || !Number.isFinite(minutes) || minutes <= 0) return SLOT_MINUTES
  return Math.max(SLOT_MINUTES, Math.ceil(minutes / SLOT_MINUTES) * SLOT_MINUTES)
}

/** Число 15-минутных слотов для строки длительности (≥ 1). */
export function durationToSlots(raw) {
  return Math.max(1, Math.round(parseDuration(raw) / SLOT_MINUTES))
}
