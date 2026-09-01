/**
 * WOLF — релиз 1.2, тикет 01: контракт массива шагов Приветственного тура.
 *
 * Движок тура полагается на инварианты формы шага (тип завершения, наличие
 * цели/события, счётчик). Тест ловит рассинхрон массива с движком до сборки:
 * без него ошибка в `onboardingSteps.js` всплыла бы только в рантайме тура.
 */
import assert from 'node:assert/strict'
import { STEPS, TOTAL_STEPS } from './src/onboardingSteps.js'

// Всего 15 сквозных шагов по заявке (в исходной нумерации пропущен номер 13).
assert.equal(TOTAL_STEPS, 15, 'TOTAL_STEPS должно быть 15')

// Массив несёт по одному объекту на каждый видимый шаг тура. Сквозной номер 2
// закреплён за экраном выбора профиля (отдельный маршрут вне движка), поэтому в
// массиве 14 записей, а не 15 — см. docstring onboardingSteps.js.
assert.equal(STEPS.length, 14, 'В массиве STEPS ожидается 14 шагов (n=2 — экран профиля вне движка)')

const AWAIT_TYPES = new Set(['nav', 'click', 'event', 'finish'])

for (const [i, s] of STEPS.entries()) {
  const where = `шаг #${i} (n=${s.n})`

  // Счётчик обязателен и в допустимом диапазоне 1..15.
  assert.ok(Number.isInteger(s.n) && s.n >= 1 && s.n <= 15, `${where}: n вне 1..15`)
  assert.ok(typeof s.title === 'string' && s.title.length > 0, `${where}: пустой title`)
  assert.ok(typeof s.text === 'string' && s.text.length > 0, `${where}: пустой text`)

  // Тип завершения — из допустимого множества.
  assert.ok(AWAIT_TYPES.has(s.await), `${where}: недопустимый await '${s.await}'`)

  // Для каждого типа завершения — своя обязательная цель.
  if (s.await === 'nav') {
    assert.ok(typeof s.key === 'string' && s.key.length > 0, `${where}: nav без key`)
  } else if (s.await === 'click') {
    assert.ok(typeof s.target === 'string' && s.target.startsWith('['), `${where}: click без селектора target`)
  } else if (s.await === 'event') {
    assert.ok(typeof s.event === 'string' && s.event.startsWith('wolf:'), `${where}: event без имени wolf:*`)
  }

  // placement — только 'anchored' | 'center'.
  assert.ok(['anchored', 'center'].includes(s.placement), `${where}: недопустимый placement '${s.placement}'`)
}

// 🐺 в каждом сообщении (сквозное требование заявки, под ADR-0008).
for (const s of STEPS) {
  assert.ok(s.title.includes('🐺'), `шаг n=${s.n}: 🐺 отсутствует в заголовке`)
}

// Счётчики строго возрастают и уникальны (нет дублей номеров шагов).
const ns = STEPS.map((s) => s.n)
for (let i = 1; i < ns.length; i++) {
  assert.ok(ns[i] > ns[i - 1], `Счётчики шагов должны строго возрастать: ${ns[i - 1]} → ${ns[i]}`)
}

// Ровно один финальный шаг (await:'finish'), и он последний.
const finishIdx = STEPS.findIndex((s) => s.await === 'finish')
assert.equal(STEPS.filter((s) => s.await === 'finish').length, 1, 'Должен быть ровно один finish-шаг')
assert.equal(finishIdx, STEPS.length - 1, 'finish-шаг должен быть последним')
assert.equal(STEPS[finishIdx].n, 15, 'Финальный шаг — «шаг 15 из 15»')

// Все четыре события сохранения сущностей присутствуют (проект/дело/время/рутина).
const events = new Set(STEPS.filter((s) => s.await === 'event').map((s) => s.event))
for (const ev of ['wolf:project-saved', 'wolf:delo-saved', 'wolf:time-entry-saved', 'wolf:routine-saved']) {
  assert.ok(events.has(ev), `Ожидается event-шаг для ${ev}`)
}

console.log(`onboardingSteps.test.mjs: OK — ${STEPS.length} шагов, счётчики ${ns.join(',')}`)
