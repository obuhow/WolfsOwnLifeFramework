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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */

/**
 * Ручной редактор распределения времени проекта (release 1.1, тикет 04).
 *
 * Чистая (без Vue) математика окна «Распределить время»: суммарные плановые часы
 * проекта (totalPlanHours) раскладываются по ISO-неделям диапазона [startDate, endDate]
 * ровной прямой, кривая делится кликом на 2 сегмента, сегменты перетаскиваются
 * вверх/вниз — при этом СУММА часов по всем неделям остаётся прежней (инвариант).
 *
 * Запись итоговых недельных значений — в «План на неделю» проекта (PUT /gantt/week-plans),
 * те же недели, что правит Гантт; здесь только вычисления, сеть — в DistributeTimeModal.
 *
 * Правило пересчёта дельты (зафиксировано решением по тикету): при изменении высоты
 * одного сегмента на Δ (суммарно Δ·w, где w — число его недель) остальные сегменты
 * пересчитываются ПРОПОРЦИОНАЛЬНО их текущим суммарным часам, и ни один сегмент не
 * уходит ниже 0 — если оставшимся не хватает ёмкости, ход изменяемого сегмента
 * ограничивается (кривая «упирается» в ноль остальных). В этом тикете сегментов всегда
 * два, поэтому «пропорционально остальным» = весь остаток дельты ложится на второй
 * сегмент; формула написана обобщённо под N сегментов.
 */

import { isoYearWeek, toMonday } from './loadChartsForecast.js'

function fmtIso(d) {
  const yyyy = d.getUTCFullYear()
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(d.getUTCDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** Округление до 0.01 ч — точность записи в week-plans (BigDecimal 2 знака). */
export function round2(v) {
  return Math.round((Number(v) + Number.EPSILON) * 100) / 100
}

/** Сумма часов по всем неделям кривой. */
export function totalHours(weeks) {
  return weeks.reduce((s, w) => s + (Number(w.hours) || 0), 0)
}

/**
 * ISO-недели диапазона [startDate, endDate] включительно.
 * Каждая неделя: { isoYear, isoWeek, weekStart (понедельник, YYYY-MM-DD), hours: 0 }.
 * Пустой массив, если диапазон невалиден (нет дат / end < start).
 * @param {string} startDate — YYYY-MM-DD
 * @param {string} endDate   — YYYY-MM-DD
 */
export function buildWeeks(startDate, endDate) {
  if (!startDate || !endDate) return []
  const start = toMonday(startDate)
  const end = toMonday(endDate)
  if (end < start) return []
  const out = []
  const cur = new Date(start.getTime())
  while (cur <= end) {
    const iso = isoYearWeek(cur)
    out.push({ isoYear: iso.isoYear, isoWeek: iso.isoWeek, weekStart: fmtIso(cur), hours: 0 })
    cur.setUTCDate(cur.getUTCDate() + 7)
  }
  return out
}

/**
 * Стартовая прямая: totalHours равномерно раскладываются по неделям.
 * База — с шагом 0.25 ч (вниз), остаток раздаётся первым неделям по 0.25 ч,
 * поэтому Σ итога отличается от N не более чем на 0.25 ч («в пределах округления
 * до 0.25 ч» из тикета), ни одна неделя не уходит в минус.
 * @param {number} totalHours
 * @param {object[]} weeks — недели из buildWeeks (hours игнорируются)
 * @returns {object[]} те же недели с часами
 */
export function straightLine(totalHours, weeks) {
  const k = weeks.length
  if (k === 0) return []
  const n = Math.max(0, Number(totalHours) || 0)
  const h = n / k
  const base = Math.floor(h * 4) / 4 // вниз до 0.25
  const rem = n - base * k // 0 ≤ rem < 0.25·k
  const extra = Math.round(rem / 0.25) // сколько первых недель получат +0.25
  return weeks.map((w, i) => ({ ...w, hours: round2(i < extra ? base + 0.25 : base) }))
}

/**
 * Средняя высота сегмента [from..to] — выпрямление сохраняет сумму сегмента.
 * @returns {number}
 */
function segmentAvg(weeks, from, to) {
  const slice = weeks.slice(from, to + 1)
  return slice.reduce((s, w) => s + (Number(w.hours) || 0), 0) / slice.length
}

/**
 * Разбить кривую на 2 сегмента точкой на неделе split (неделя split входит в
 * сегмент A). Оба сегмента выпрямляются до средней высоты — Σ по неделям
 * сохраняется точно. Повторная разбивка на той же неделе возвращает кривую
 * к одному сегменту (также выпрямленному).
 * @param {object[]} weeks
 * @param {number|null} split — индекс недели-точки; null = разбивки нет
 * @returns {{ weeks: object[], splitIndex: number|null }}
 */
export function splitAt(weeks, split) {
  const k = weeks.length
  if (split == null) {
    const avg = k > 0 ? totalHours(weeks) / k : 0
    return { weeks: weeks.map((w) => ({ ...w, hours: round2(avg) })), splitIndex: null }
  }
  if (split <= 0 || split >= k - 1) return { weeks, splitIndex: null }
  const hA = segmentAvg(weeks, 0, split)
  const hB = segmentAvg(weeks, split + 1, k - 1)
  return {
    weeks: weeks.map((w, i) => ({ ...w, hours: i <= split ? hA : hB })),
    splitIndex: split,
  }
}

/**
 * Изменить высоту сегмента seg (0 = A, 1 = B) до targetHours (часы на неделю).
 * Инвариант: Σ часов по всем неделям НЕ меняется. Дельта Δ·w раскладывается на
 * остальные сегменты пропорционально их текущим суммарным часам; ни один сегмент
 * не уходит ниже 0 — при нехватке ёмкости ход ограничивается (actual < target).
 *
 * @param {object[]} weeks — текущая кривая (должна быть разбита: splitIndex != null)
 * @param {number} splitIndex — индекс недели-точки разбивки
 * @param {number} seg — 0 или 1
 * @param {number} targetHours — целевая высота сегмента (из жеста/поля)
 * @returns {{ weeks: object[], actual: number }} — actual = реальная высота после клампа
 */
export function adjustSegment(weeks, splitIndex, seg, targetHours) {
  const k = weeks.length
  if (splitIndex == null || k < 2) {
    // Без разбивки перетаскивание не меняет Σ — запрещено; возвращаем как есть.
    return { weeks: [...weeks], actual: k > 0 ? totalHours(weeks) / k : 0 }
  }
  const aCount = splitIndex + 1
  const bCount = k - aCount
  const sumA = weeks.slice(0, aCount).reduce((s, w) => s + (Number(w.hours) || 0), 0)
  const sumB = weeks.slice(aCount).reduce((s, w) => s + (Number(w.hours) || 0), 0)
  const hA = sumA / aCount
  const hB = sumB / bCount
  let newA = hA
  let newB = hB
  let actual
  if (seg === 0) {
    // A тянем; B поглощает дельту (пропорционально — единственный «остальной»).
    const maxA = hA + sumB / aCount // предел: B ровно 0
    newA = Math.max(0, Math.min(targetHours, maxA))
    const d = (newA - hA) * aCount
    newB = bCount > 0 ? (sumB - d) / bCount : 0
    actual = newA
  } else {
    const maxB = hB + sumA / bCount // предел: A ровно 0
    newB = Math.max(0, Math.min(targetHours, maxB))
    const d = (newB - hB) * bCount
    newA = aCount > 0 ? (sumA - d) / aCount : 0
    actual = newB
  }
  return {
    weeks: weeks.map((w, i) => ({ ...w, hours: i < aCount ? newA : newB })),
    actual,
  }
}
