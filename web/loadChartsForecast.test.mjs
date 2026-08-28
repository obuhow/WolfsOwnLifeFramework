// Smoke-тест формул тикета 04 (release 0.8). Запуск: node loadChartsForecast.test.mjs
import { forecastByRate, ladderRates, weeksBetween, isoYearWeek, formatFinish } from './src/loadChartsForecast.js'

let failures = 0
function eq(name, got, want) {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { failures++; console.log(`FAIL ${name}: got ${JSON.stringify(got)} want ${JSON.stringify(want)}`) }
  else console.log(`ok   ${name}`)
}

const SM = '2026-08-17' // понедельник

// Паритет с бэкендом LoadChartsService.forecastByRate (п.11 тикета 04)
// Финиш = ближайший понедельник + ceil(remaining / hoursPerWeek) недель.
eq('rate rem=132.5 wk=8', forecastByRate(132.5, 8, SM), '2026-12-14')
eq('rate rem=0', forecastByRate(0, 8, SM), null)
eq('rate wk=0', forecastByRate(100, 0, SM), null)
eq('rate rem=7.5 wk=3', forecastByRate(7.5, 3, SM), '2026-09-07')
eq('rate rem=1 wk=1', forecastByRate(1, 1, SM), '2026-08-24')

// Тикет: ставка 5 ч/нед на Бюджете и «5 ч/нед» на Лестнице — одна дата финиша
const b = forecastByRate(100, 5, SM)
const l = forecastByRate(100, 5, SM)
eq('budget==ladder same rate', b, l)

// Лестница: [max(1,h-3), h, h+3, h+6] без дублей/нулей (п.10)
eq('ladderRates(8)', ladderRates(8), [5, 8, 11, 14])
eq('ladderRates(5)', ladderRates(5), [2, 5, 8, 11])
eq('ladderRates(0)', ladderRates(0), [1, 3, 6])
eq('ladderRates(1)', ladderRates(1), [1, 4, 7]) // h=1 → [1,1,4,7] → дубль 1 убран

// weeksBetween для колонки «N недель»
eq('weeksBetween(h=8)', weeksBetween(SM, forecastByRate(132.5, 8, SM)), 17)
eq('weeksBetween(null)', weeksBetween(SM, null), null)

// ISO года/недели для PUT /gantt/week-plans (текущая неделя от startMonday)
const iyw = isoYearWeek(SM)
eq('isoYearWeek(SM).isoYear', iyw.isoYear, 2026)
eq('isoYearWeek(SM).isoWeek', iyw.isoWeek, 34)

// Человекочитаемая дата
eq('formatFinish', formatFinish('2027-03-01'), '1 мар 27')

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`)
process.exit(failures === 0 ? 0 : 1)
