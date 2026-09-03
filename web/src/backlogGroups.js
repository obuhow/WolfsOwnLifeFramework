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
          pending: pid == null ? null : (hours ? hours.pending : 0),
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

/**
 * Backlog grouped for the Ежедневник fill-bar (ticket 02, release 1.3).
 *
 * Unlike {@link groupByProject} (which enumerates delos), this builds the list
 * from **projects that have a weekly norm** — every project with `plan > 0` in
 * the Gantt week aggregate appears, even with zero linked delos (empty bar,
 * «0 / y ч»). Owner's request: «список Проектов, у которых на этой неделе
 * запланирована норма часов». Delos merely fill the bar and expand under their
 * group. Delos linked to a project that has no norm still surface their project
 * group (so nothing linked silently disappears). Unlinked delos fall under a
 * single «Без проекта» group, shown only when such delos exist.
 *
 * All hour values come from `hoursByProject` (the real Gantt aggregate), never
 * from the visible page.
 *
 * @param {Array} projects  project list for titles ({id, title})
 * @param {Array} delos      backlog entries carrying `projectIds`
 * @param {Object} hoursByProject { [projectId]: { plan, fact, pending } }
 * @returns {Array} groups sorted by label, «Без проекта» last
 */
export function groupBacklogWithNorm(projects, delos, hoursByProject = {}) {
  const titleOf = id => projects.find(p => p.id === id)?.title || `Проект #${id}`
  const groups = new Map()

  const ensure = pid => {
    const key = pid == null ? '__none__' : String(pid)
    if (!groups.has(key)) {
      const hours = pid == null ? null : hoursByProject[String(pid)]
      groups.set(key, {
        key,
        projectId: pid,
        label: pid == null ? 'Без проекта' : titleOf(pid),
        fact: pid == null ? null : (hours ? hours.fact : 0),
        plan: pid == null ? null : (hours ? hours.plan : null),
        pending: pid == null ? null : (hours ? hours.pending : 0),
        items: []
      })
    }
    return groups.get(key)
  }

  // Seed every project that has a weekly norm (plan > 0) — even with no delos.
  for (const [pid, hours] of Object.entries(hoursByProject)) {
    const plan = Number(hours?.plan)
    if (Number.isFinite(plan) && plan > 0) ensure(Number(pid))
  }

  // Attach delos; a linked project without a norm still gets its group.
  for (const delo of delos) {
    const pids = (delo.projectIds && delo.projectIds.length) ? delo.projectIds : [null]
    for (const pid of pids) {
      const group = ensure(pid)
      if (!group.items.some(x => x.id === delo.id)) group.items.push(delo)
    }
  }

  return Array.from(groups.values())
    .map(g => ({ ...g, fillBar: g.projectId == null ? null : fillBarSegments(g) }))
    .sort((a, b) => {
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

/**
 * Полоса заполнения проекта (ticket 06, release 1.1; ADR-0006).
 *
 * Три сегмента относительно недельного плана Проекта (`plan`, ч):
 *   - fact    — тёмный зелёный, доля выполненного факта (status=DONE);
 *   - pending — светло-зелёный, доля запланированного (status=PLANNED, ещё не
 *     подтверждено) — берётся из отдельного агрегата `pendingHours`
 *     (GET /gantt cells[].pendingHours), НЕ из `plan − fact` (расходится с
 *     примером тикета: план 8ч, факт 1ч, запланирован ещё 1ч → полоса
 *     заполнена на 2/8, а не на 8/8);
 *   - остаток — нейтральный (пусто).
 * Перегруз (fact+pending > plan) — доли клампятся до 100%, признак overLimit
 * выставляется отдельно (рендерится нейтральной подписью/штриховкой, без
 * красного — п.3 тикета).
 *
 * @param {{ fact: number|null, plan: number|null, pending: number|null }} group
 * @returns {{ factPct: number, pendingPct: number, overLimit: boolean } | null}
 *   null когда план не задан (полосу показывать нечего — только подпись «—»).
 */
export function fillBarSegments(group) {
  const plan = Number(group?.plan)
  if (!Number.isFinite(plan) || plan <= 0) return null
  const fact = Math.max(0, Number(group?.fact) || 0)
  const pending = Math.max(0, Number(group?.pending) || 0)
  const factPct = Math.min(100, (fact / plan) * 100)
  const pendingPct = Math.min(100 - factPct, (pending / plan) * 100)
  return {
    factPct,
    pendingPct,
    overLimit: (fact + pending) > plan
  }
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
        fact: Number(cell.factHours || 0),
        pending: Number(cell.pendingHours || 0)
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
