// Smoke-тест полосы заполнения проекта (тикет 06, release 1.1, ADR-0006).
// + список групп от проектов с недельной нормой (тикет 02, release 1.3).
// Запуск: node backlogGroups.test.mjs
import { fillBarSegments, groupBacklogWithNorm } from './src/backlogGroups.js'

let failures = 0
function eq(name, got, want) {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) {
    failures++
    console.log(`FAIL ${name}: got ${JSON.stringify(got)} want ${JSON.stringify(want)}`)
  } else console.log(`ok   ${name}`)
}
function approx(name, got, want, tol = 1e-9) {
  const ok = Math.abs(got - want) <= tol
  if (!ok) {
    failures++
    console.log(`FAIL ${name}: got ${got} want ~${want} (±${tol})`)
  } else console.log(`ok   ${name}`)
}

// Пример из тикета: план 8ч, факт 1ч (пн), запланирован ещё 1ч (ср, не выполнено)
// → полоса: 1/8 тёмным (факт), ещё 1/8 (до 2/8) светлым (план), остальное пусто.
const t1 = fillBarSegments({ plan: 8, fact: 1, pending: 1 })
approx('ticket example: factPct', t1.factPct, 12.5)
approx('ticket example: pendingPct', t1.pendingPct, 12.5)
eq('ticket example: overLimit', t1.overLimit, false)

// План не задан — полосы нет.
eq('no plan -> null', fillBarSegments({ plan: null, fact: 1, pending: 1 }), null)
eq('zero plan -> null', fillBarSegments({ plan: 0, fact: 1, pending: 1 }), null)

// Полностью выполнено, ничего не запланировано.
const t2 = fillBarSegments({ plan: 8, fact: 8, pending: 0 })
approx('full fact: factPct', t2.factPct, 100)
approx('full fact: pendingPct', t2.pendingPct, 0)
eq('full fact: overLimit', t2.overLimit, false)

// Перегруз: план 8ч, поставлено (факт+план) 10ч — доли клампятся до 100% суммарно,
// признак overLimit выставлен (рендерится нейтральной штриховкой, без красного).
const t3 = fillBarSegments({ plan: 8, fact: 4, pending: 6 })
approx('overload: factPct', t3.factPct, 50)
approx('overload: pendingPct', t3.pendingPct, 50) // clamp: 100-50=50, а не 75
eq('overload: overLimit', t3.overLimit, true)
approx('overload: segments sum to 100', t3.factPct + t3.pendingPct, 100)

// Отрицательные/NaN входы не уводят полосу в минус.
const t4 = fillBarSegments({ plan: 8, fact: -1, pending: null })
approx('negative fact clamped', t4.factPct, 0)
approx('null pending clamped', t4.pendingPct, 0)

// --- groupBacklogWithNorm: список от проектов с недельной нормой (тикет 02) ---

const projects = [
  { id: 1, title: 'Курс английского' },
  { id: 2, title: 'Бег' },
  { id: 3, title: 'Проект без нормы' }
]

// Норма у проекта без единого Дела — проект всё равно в списке, полоса пустая.
{
  const hours = { '2': { plan: 5, fact: 0, pending: 0 } }
  const groups = groupBacklogWithNorm(projects, [], hours)
  eq('norm-no-delos: одна группа', groups.length, 1)
  eq('norm-no-delos: это проект 2', groups[0].projectId, 2)
  eq('norm-no-delos: подпись 0 / 5 ч', groupHoursLabelLocal(groups[0]), '0 / 5 ч')
  eq('norm-no-delos: полосы нет пока? fillBar факт=0/pending=0', fillBarSegments(groups[0]), { factPct: 0, pendingPct: 0, overLimit: false })
  eq('norm-no-delos: items пусты', groups[0].items.length, 0)
}

// Пример владельца: проект 1 план 8ч, факт 1ч, pending 1ч, два Дела привязаны.
{
  const hours = { '1': { plan: 8, fact: 1, pending: 1 } }
  const delos = [
    { id: 10, projectIds: [1], title: 'Урок пн' },
    { id: 11, projectIds: [1], title: 'Урок ср' }
  ]
  const groups = groupBacklogWithNorm(projects, delos, hours)
  eq('owner-example: одна группа', groups.length, 1)
  eq('owner-example: 2 Дела под проектом', groups[0].items.length, 2)
  const seg = fillBarSegments(groups[0])
  approx('owner-example: factPct', seg.factPct, 12.5)
  approx('owner-example: pendingPct', seg.pendingPct, 12.5)
}

// Проекты с нормой БЕЗ Дел + проект с Делами: все попадают, сортировка по названию.
{
  const hours = {
    '1': { plan: 8, fact: 0, pending: 0 }, // Курс английского
    '2': { plan: 5, fact: 0, pending: 0 }  // Бег
  }
  const delos = [{ id: 20, projectIds: [1], title: 'Урок' }]
  const groups = groupBacklogWithNorm(projects, delos, hours)
  eq('mix: две группы (оба проекта с нормой)', groups.length, 2)
  eq('mix: сортировка — Бег первым', groups[0].label, 'Бег')
  eq('mix: Курс английского вторым', groups[1].label, 'Курс английского')
}

// Проект БЕЗ нормы (plan отсутствует), но с привязанным Делом — не теряется.
{
  const hours = {} // ни у кого нет нормы
  const delos = [{ id: 30, projectIds: [3], title: 'Задача' }]
  const groups = groupBacklogWithNorm(projects, delos, hours)
  eq('linked-no-norm: проект 3 показан', groups.length, 1)
  eq('linked-no-norm: это проект 3', groups[0].projectId, 3)
  eq('linked-no-norm: plan == null', groups[0].plan, null)
}

// Дело без проекта → «Без проекта» в конце, показывается только при наличии таких Дел.
{
  const hours = { '2': { plan: 5, fact: 0, pending: 0 } }
  const delos = [{ id: 40, projectIds: [], title: 'Разрозненное' }]
  const groups = groupBacklogWithNorm(projects, delos, hours)
  eq('no-project: две группы', groups.length, 2)
  eq('no-project: «Без проекта» последней', groups[groups.length - 1].label, 'Без проекта')
}

// plan = 0 не считается нормой — проект без Дел с plan=0 не появляется.
{
  const hours = { '2': { plan: 0, fact: 0, pending: 0 } }
  const groups = groupBacklogWithNorm(projects, [], hours)
  eq('zero-plan-no-delos: пусто', groups.length, 0)
}

// Локальная копия подписи (в компонентах это groupHoursLabel из backlogGroups.js).
function groupHoursLabelLocal(group) {
  const f = group.fact == null ? '—' : (Number.isInteger(group.fact) ? String(group.fact) : String(group.fact))
  const p = group.plan == null ? '—' : (Number.isInteger(group.plan) ? String(group.plan) : String(group.plan))
  return `${f} / ${p} ч`
}

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`)
process.exit(failures === 0 ? 0 : 1)
