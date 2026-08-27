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
 * Shared project-grouped backlog helpers (tickets 03, 04, 08).
 *
 * All hour values come from real API aggregates — never computed from the
 * visible page. A missing plan renders as «—», never 0.
 */

export function formatHours(value) {
  if (value == null || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
}

export function hoursOrDash(value) {
  if (value == null) return '—'
  return formatHours(value)
}

/**
 * Group backlog entries by Project.
 * @param {Array} items entries carrying `projectIds`
 * @param {Array} projects project list for titles
 * @param {Object} hoursByProject { [projectId]: { plan, fact } } from the Gantt aggregate
 */
export function groupByProject(items, projects, hoursByProject = {}) {
  const titleOf = id => projects.find(p => p.id === id)?.title || `Проект #${id}`
  const groups = new Map()

  for (const item of items) {
    const pids = (item.projectIds && item.projectIds.length) ? item.projectIds : [null]
    for (const pid of pids) {
      const key = pid == null ? '__none__' : String(pid)
      if (!groups.has(key)) {
        const hours = pid == null ? null : hoursByProject[String(pid)]
        groups.set(key, {
          key,
          projectId: pid,
          label: pid == null ? 'Без проекта' : titleOf(pid),
          fact: pid == null ? null : (hours ? hours.fact : 0),
          plan: pid == null ? null : (hours ? hours.plan : null),
          items: []
        })
      }
      const group = groups.get(key)
      if (!group.items.some(x => x.id === item.id)) group.items.push(item)
    }
  }

  return Array.from(groups.values()).sort((a, b) => {
    if (a.key === '__none__') return 1
    if (b.key === '__none__') return -1
    return a.label.localeCompare(b.label, 'ru')
  })
}

/** «x / y ч» for a project group; empty for the «Без проекта» group. */
export function groupHoursLabel(group) {
  if (group.projectId == null) return ''
  return `${hoursOrDash(group.fact ?? 0)} / ${hoursOrDash(group.plan)} ч`
}

/** Real per-project plan/fact hours for one ISO week from the Gantt aggregate. */
export async function fetchProjectWeekHours(apiBase, headers, mondayIso) {
  if (!mondayIso) return {}
  try {
    const res = await fetch(`${apiBase}/gantt?from=${mondayIso}&weeks=1`, { headers })
    if (!res.ok) return {}
    const body = await res.json()
    const map = {}
    for (const row of body.projects || []) {
      const cell = (row.cells || [])[0] || {}
      map[String(row.id)] = {
        plan: cell.planHours == null ? null : Number(cell.planHours),
        fact: Number(cell.factHours || 0)
      }
    }
    return map
  } catch {
    return {}
  }
}

/** Monday (ISO) of the week containing the given date. */
export function mondayOf(date) {
  const d = new Date(date)
  const shift = (d.getDay() + 6) % 7
  d.setDate(d.getDate() - shift)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** ISO year-week id, e.g. 2026-W34. */
export function isoWeekId(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7))
  const week1 = new Date(d.getFullYear(), 0, 4)
  const week = 1 + Math.round(((d - week1) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7)
  return `${d.getFullYear()}-W${String(week).padStart(2, '0')}`
}
