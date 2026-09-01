/**
 * WOLF — релиз 1.2, тикет 07: разбор человеческого формата длительности.
 *
 * Тест закрывает Testing Decisions тикета: разные формы записи «полтора часа»
 * должны давать 6 слотов по 15 минут; округление вверх до кратности 15; пустой
 * ввод — 1 слот (регресс на прежнее поведение «один клик = один слот»).
 */
import assert from 'node:assert/strict'
import { parseDuration, durationToSlots, SLOT_MINUTES } from './src/durationParse.js'

assert.equal(SLOT_MINUTES, 15)

// «1 ч 30 м» и все его написания → 90 минут = 6 слотов.
for (const form of ['1 ч 30 м', '1ч30м', '90 м', '90 минут', '90 мин', '1.5 ч', '1,5 ч', '1:30', '90']) {
  assert.equal(parseDuration(form), 90, `parseDuration('${form}') должно быть 90`)
  assert.equal(durationToSlots(form), 6, `durationToSlots('${form}') должно быть 6`)
}

// Пустой ввод / мусор → 15 минут = 1 слот (регресс на «один слот»).
for (const form of ['', '   ', 'abc', null, undefined]) {
  assert.equal(parseDuration(form), 15, `parseDuration('${form}') должно быть 15`)
  assert.equal(durationToSlots(form), 1, `durationToSlots('${form}') должно быть 1`)
}

// Округление ВВЕРХ до кратности 15: «20 м» → 30 (2 слота).
assert.equal(parseDuration('20 м'), 30, '«20 м» округляется вверх до 30')
assert.equal(durationToSlots('20 м'), 2, '«20 м» → 2 слота')
assert.equal(parseDuration('7 м'), 15, '«7 м» → 15 (не меньше слота)')
assert.equal(parseDuration('46 минут'), 60, '«46 минут» → 60 (вверх до кратности 15)')

// Часы без минут и минуты без часов.
assert.equal(parseDuration('2 ч'), 120, '«2 ч» → 120')
assert.equal(durationToSlots('2 ч'), 8, '«2 ч» → 8 слотов')
assert.equal(parseDuration('45 м'), 45, '«45 м» → 45')

// «1:05» → 65 → вверх до 75 (5 слотов).
assert.equal(parseDuration('1:05'), 75, '«1:05» = 65 мин → 75 (вверх)')
assert.equal(durationToSlots('1:05'), 5)

// Нормализация поля минут ≥ 60: «1:75» = 1ч + 75мин = 135 (кратно 15, 9 слотов).
assert.equal(parseDuration('1:75'), 135, '«1:75» нормализуется в 135 мин')
assert.equal(durationToSlots('1:75'), 9)

// Точное кратное 15 не «раздувается».
assert.equal(parseDuration('30 м'), 30)
assert.equal(parseDuration('1 ч'), 60)

console.log('durationParse.test.mjs: OK — форматы длительности, округление вверх, дефолт 1 слот')
