// Smoke-тест полосы заполнения проекта (тикет 06, release 1.1, ADR-0006).
// Запуск: node backlogGroups.test.mjs
import { fillBarSegments } from './src/backlogGroups.js'

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

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`)
process.exit(failures === 0 ? 0 : 1)
