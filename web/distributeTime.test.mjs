// Smoke-тест редактора распределения времени (тикет 04, release 1.1).
// Запуск: node distributeTime.test.mjs
import {
  buildWeeks,
  straightLine,
  splitAt,
  adjustSegment,
  totalHours,
} from './src/distributeTime.js'

let failures = 0
function eq(name, got, want, tol = 0) {
  const ok = tol > 0 ? Math.abs(got - want) <= tol : JSON.stringify(got) === JSON.stringify(want)
  if (!ok) {
    failures++
    console.log(`FAIL ${name}: got ${JSON.stringify(got)} want ${JSON.stringify(want)}`)
  } else console.log(`ok   ${name}`)
}
function approx(name, got, want, tol) {
  const ok = Math.abs(got - want) <= tol
  if (!ok) {
    failures++
    console.log(`FAIL ${name}: got ${got} want ~${want} (±${tol})`)
  } else console.log(`ok   ${name}`)
}

// --- buildWeeks: ISO-недели диапазона --------------------------------------
const w = buildWeeks('2026-08-17', '2026-08-30') // пн..вс — 2 ISO-недели
eq('weeks count', w.length, 2)
eq('first weekStart', w[0].weekStart, '2026-08-17')
eq('first isoWeek', w[0].isoWeek, 34)
eq('second weekStart', w[1].weekStart, '2026-08-24')
eq('second isoWeek', w[1].isoWeek, 35)
// даты внутри недели приводятся к понедельнику
eq('mid-week start', buildWeeks('2026-08-19', '2026-08-21').length, 1)
eq('invalid range', buildWeeks('2026-09-01', '2026-08-17').length, 0)
eq('empty dates', buildWeeks(null, '2026-08-17').length, 0)

// --- straightLine: прямая по Σ=N на K недель -------------------------------
const N = 100
const K = 10
const line = straightLine(N, buildWeeks('2026-08-17', '2026-10-25'))
eq('line weeks count', line.length, K)
eq('line per-week N/K', line[0].hours, 10)
approx('line sum == N', totalHours(line), N, 0.25)
for (const wk of line) eq('line non-negative', wk.hours >= 0, true)

// дробный остаток: Σ в пределах 0.25 ч от N
const N2 = 100.1
const line2 = straightLine(N2, buildWeeks('2026-08-17', '2026-09-27')) // 7 недель
approx('line2 sum ~ N2', totalHours(line2), N2, 0.25)
for (const wk of line2) eq('line2 non-negative', wk.hours >= 0, true)

// нулевой объём → нулевая прямая
eq('line zero', straightLine(0, buildWeeks('2026-08-17', '2026-08-30')).map((x) => x.hours), [0, 0])

// --- splitAt: разбивка на 2 сегмента, Σ сохраняется ------------------------
const s = splitAt(line, 4) // точка на неделе 4 → A: 0..4 (5 нед), B: 5..9 (5 нед)
eq('split index', s.splitIndex, 4)
eq('split A hours', s.weeks[0].hours, 10)
eq('split B hours', s.weeks[5].hours, 10)
approx('split sum == N', totalHours(s.weeks), N, 1e-9)

// выпрямление сегментов с разной высотой сохраняет сумму
const step = [12, 12, 12, 8, 8].map((hours) => ({ hours }))
const s2 = splitAt(step, 2)
eq('s2 A avg', s2.weeks[0].hours, 12)
eq('s2 B avg', s2.weeks[3].hours, 8)
approx('s2 sum preserved', totalHours(s2.weeks), totalHours(step), 1e-9)

// снятие разбивки → один сегмент (прямая), Σ сохраняется
const un = splitAt(s.weeks, null)
eq('unsplit single', un.splitIndex, null)
approx('unsplit sum == N', totalHours(un.weeks), N, 1e-9)

// --- adjustSegment: инвариант Σ = const при перетаскивании ------------------
// Прямая 10 ч × 10, разбита 5+5.
let cur = splitAt(straightLine(N, buildWeeks('2026-08-17', '2026-10-25')), 4).weeks

// подъём A на +4 (10 → 14): B должен опуститься на 4 (поглотил всю дельту)
const up = adjustSegment(cur, 4, 0, 14)
eq('up A hours', up.weeks[0].hours, 14)
eq('up B hours', up.weeks[5].hours, 6)
approx('up sum == N', totalHours(up.weeks), N, 1e-6)
eq('up actual == target', up.actual, 14)

// опускание A на −6 (10 → 4): B поднимается на 6
const down = adjustSegment(cur, 4, 0, 4)
eq('down A hours', down.weeks[0].hours, 4)
eq('down B hours', down.weeks[5].hours, 16)
approx('down sum == N', totalHours(down.weeks), N, 1e-6)

// кламп: A нельзя поднять так, чтобы B ушёл в минус (maxA = 10 + 50/5 = 20)
const clamp = adjustSegment(cur, 4, 0, 100)
eq('clamp A hours', clamp.weeks[0].hours, 20)
eq('clamp B hours', clamp.weeks[5].hours, 0)
eq('clamp actual limited', clamp.actual, 20)
approx('clamp sum == N', totalHours(clamp.weeks), N, 1e-6)

// симметрия: тянем B вниз до 0 → A забирает всю дельту
const bDown = adjustSegment(cur, 4, 1, 0)
eq('bDown A hours', bDown.weeks[0].hours, 20)
eq('bDown B hours', bDown.weeks[5].hours, 0)
approx('bDown sum == N', totalHours(bDown.weeks), N, 1e-6)

// без разбивки перетаскивание запрещено (Σ не меняется, высота не трогается)
const noSplit = adjustSegment(line, null, 0, 50)
approx('no-split sum unchanged', totalHours(noSplit.weeks), N, 1e-9)
eq('no-split hours untouched', noSplit.weeks[0].hours, 10)

// ни одна неделя не уходит в минус ни при каком жесте
for (const wk of [...up.weeks, ...down.weeks, ...clamp.weeks, ...bDown.weeks]) {
  eq('never negative', wk.hours >= -1e-9, true)
}

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`)
process.exit(failures === 0 ? 0 : 1)
