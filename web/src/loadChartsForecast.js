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
 * Единая формула прогноза финиша по ставке часов (release 0.8, тикет 04).
 *
 * Дословная копия бэкенд-метода LoadChartsService.forecastByRate, чтобы
 * «Бюджет» (2a) и «Лестница» (2b) давали одну и ту же дату финиша на
 * одинаковой ставке — расхождение между вкладками недопустимо (см. п.11 тикета 04).
 *
 *   weeks  = remainingHours / hoursPerWeek   (ставка 0 либо объём 0 → финиша нет)
 *   finish = ближайший понедельник + ceil(weeks) недель
 *
 * @param {number} remainingHours — остаток работ (часы)
 * @param {number} hoursPerWeek   — ставка «часов в неделю»
 * @param {Date|string} startMonday — ближайший понедельник (точка отсчёта)
 * @returns {string|null} дата финиша в формате YYYY-MM-DD либо null
 */
export function forecastByRate(remainingHours, hoursPerWeek, startMonday) {
  if (!(remainingHours > 0)) return null
  if (!(hoursPerWeek > 0)) return null
  const weeks = remainingHours / hoursPerWeek
  const ceilWeeks = Math.ceil(weeks)
  const base = toMonday(startMonday)
  const finish = plusWeeks(base, ceilWeeks)
  return fmtIso(finish)
}

/**
 * Четыре соседние ставки для «Лестницы» (2b), рассчитанные от текущей ставки h
 * как [max(1, h−3), h, h+3, h+6] с отбрасыванием дублей и нулей (п.10).
 * @param {number} h — текущая ставка часов в неделю
 * @returns {number[]} уникальные ставки по возрастанию
 */
export function ladderRates(h) {
  const base = h > 0 ? h : 0
  const raw = [Math.max(1, base - 3), base, base + 3, base + 6]
  // отбрасываем нули и дубли, сохраняем порядок возрастания
  const seen = new Set()
  const out = []
  for (const v of raw) {
    if (v <= 0) continue
    if (seen.has(v)) continue
    seen.add(v)
    out.push(v)
  }
  return out.sort((a, b) => a - b)
}

/** Ближайший понедельник к заданной дате (совпадает с бэкендом). */
export function toMonday(d) {
  const date = d instanceof Date ? new Date(d.getTime()) : new Date(d)
  const day = (date.getUTCDay() + 6) % 7 // 0 = понедельник
  date.setUTCDate(date.getUTCDate() - day)
  date.setUTCHours(0, 0, 0, 0)
  return date
}

/** Число недель между ближайшим понедельником и финишем (для колонки «N недель»). */
export function weeksBetween(startMonday, finishIso) {
  if (!finishIso) return null
  const start = toMonday(startMonday).getTime()
  const finish = new Date(finishIso + 'T00:00:00Z').getTime()
  if (Number.isNaN(finish)) return null
  const diffMs = finish - start
  if (diffMs < 0) return 0
  return Math.round(diffMs / (7 * 24 * 3600 * 1000))
}

function plusWeeks(d, n) {
  const copy = new Date(d.getTime())
  copy.setUTCDate(copy.getUTCDate() + 7 * n)
  return copy
}

function fmtIso(d) {
  return d.toISOString().slice(0, 10)
}

/** Человекочитаемая дата «14 мар 27» (русские сокращения месяцев). */
const MONTHS_RU = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']
export function formatFinish(iso) {
  if (!iso) return '—'
  const d = new Date(iso + 'T00:00:00Z')
  if (Number.isNaN(d.getTime())) return '—'
  return `${d.getUTCDate()} ${MONTHS_RU[d.getUTCMonth()]} ${String(d.getUTCFullYear()).slice(2)}`
}

/** Часы с подписью «8 ч» / «0 ч». */
export function formatHours(h) {
  const n = Math.round(Number(h))
  return `${n} ч`
}

/**
 * ISO-8601 год и номер недели для даты (совпадает с бэкенд-WeekFields.ISO).
 * Используется для PUT /gantt/week-plans на текущую неделю — бэкенд ищет
 * WeekPlan именно по этим isoYear/isoWeek, вычисленным от startMonday.
 * @param {Date|string} d
 * @returns {{ isoYear: number, isoWeek: number }}
 */
export function isoYearWeek(d) {
  const date = d instanceof Date ? new Date(d.getTime()) : new Date(d + 'T00:00:00Z')
  // ближайший четверг той же недели
  const dayNum = (date.getUTCDay() + 6) % 7 // пн=0
  date.setUTCDate(date.getUTCDate() - dayNum + 3)
  const firstThursday = new Date(Date.UTC(date.getUTCFullYear(), 0, 4))
  const week = 1 + Math.round(
    ((date - firstThursday) / 86400000 - 3 + ((firstThursday.getUTCDay() + 6) % 7)) / 7
  )
  return { isoYear: date.getUTCFullYear(), isoWeek: week }
}

/**
 * Пересчёт суммарной загрузки по месяцам на клиенте (release 0.8, тикет 07).
 *
 * Дословная копия бэкенд-формулы LoadChartsService.accumulateProjectMonthly +
 * вклад рутин, чтобы фильтр «какие проекты/рутины отображать» пересчитывал
 * capacity мгновенно, без перезапроса к серверу. При выборе всех проектов и
 * всех рутин результат совпадает с серверным monthlyLoad.
 *
 * @param {object[]} projects            — data.projects (с полями id, curve, weeklyPlanHours)
 * @param {object[]} routines            — data.routines (с полями id, weeklyHours)
 * @param {number}    weeklyLimit        — недельная норма (для флага overLimit)
 * @param {string}    startMonday        — дата начала горизонта (YYYY-MM-DD)
 * @param {number}    horizonMonths      — длина горизонта
 * @param {Set<number>|null} selectedProjectIds — выбранные id проектов (null = все)
 * @param {Set<number>|null} selectedRoutineIds — выбранные id рутин (null = все)
 * @returns {{month: string, hours: number, overLimit: boolean}[]}
 */
const WEEKS_PER_MONTH = 4.33
function yearMonthList(startMonday, horizonMonths) {
  const out = []
  const d = new Date(startMonday + 'T00:00:00')
  for (let i = 0; i < horizonMonths; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
    d.setMonth(d.getMonth() + 1)
  }
  return out
}
function monthStartMondayIso(ym) {
  const [y, m] = ym.split('-').map(Number)
  let d = new Date(y, m - 1, 1)
  const dow = (d.getDay() + 6) % 7
  d = new Date(d.getFullYear(), d.getMonth(), d.getDate() - dow)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}
// Часы в неделю по кривой для месяца — ступенчато по weekStart.
// Точная копия LoadChartsService.hoursAt: для первого месяца понедельник
// начала недели может быть раньше startMonday — тогда берём startMonday.
function hoursAtMonth(curve, startMonday, ym) {
  let monthStart = monthStartMondayIso(ym)
  if (monthStart < startMonday) monthStart = startMonday
  let rate = 0
  let active = null
  for (const e of curve) {
    if (e.weekStart <= monthStart) {
      if (active === null || e.weekStart > active) {
        active = e.weekStart
        rate = Number(e.hours) || 0
      }
    }
  }
  return rate
}
function scale2(v) {
  return Math.round(Number(v) * 100) / 100
}
export function computeMonthlyLoad(
  projects,
  routines,
  weeklyLimit,
  startMonday,
  horizonMonths,
  selectedProjectIds,
  selectedRoutineIds
) {
  const months = yearMonthList(startMonday, horizonMonths)
  const projectMonthly = new Array(months.length).fill(0)
  const selP = selectedProjectIds
  const selR = selectedRoutineIds

  for (const p of projects) {
    if (selP && !selP.has(p.id)) continue
    const curve = p.curve || []
    if (curve.length === 0) {
      const monthly = scale2(Number(p.weeklyPlanHours) || 0) * WEEKS_PER_MONTH
      for (let i = 0; i < months.length; i++) projectMonthly[i] += monthly
    } else {
      for (let i = 0; i < months.length; i++) {
        const hpw = hoursAtMonth(curve, startMonday, months[i])
        projectMonthly[i] += scale2(hpw) * WEEKS_PER_MONTH
      }
    }
  }

  let routineMonthlyTotal = 0
  for (const r of routines) {
    if (selR && !selR.has(r.id)) continue
    routineMonthlyTotal += scale2(Number(r.weeklyHours) || 0) * WEEKS_PER_MONTH
  }

  return months.map((m, i) => {
    const hours = scale2(projectMonthly[i] + routineMonthlyTotal)
    return { month: m, hours, overLimit: hours > Number(weeklyLimit || 0) }
  })
}
