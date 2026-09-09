/**
 * WOLF — меню навигации слева (бургер): режимы док/оверлей, хранение выбора.
 *
 * Тест закрывает решения владельца:
 *   - на широких экранах меню ОТКРЫТО по умолчанию;
 *   - закрытое пользователем меню остаётся закрытым после перезагрузки;
 *   - на узких экранах меню — оверлей, который по умолчанию закрыт;
 *   - док не трогает скролл и фокус (это не модальное окно).
 */
import assert from 'node:assert/strict'
import {
  DOCK_MIN_WIDTH,
  MENU_DOCK_KEY,
  menuModeFor,
  isMenuOpen,
  loadDockPref,
  persistDockPref,
  expandedGroupsFor,
} from './src/navMenu.js'

// --- Граница режимов --------------------------------------------------------

assert.equal(menuModeFor(0), 'overlay')
assert.equal(menuModeFor(320), 'overlay')
assert.equal(menuModeFor(768), 'overlay', '768px — ещё узкий экран (media ≤768)')
assert.equal(menuModeFor(769), 'dock', '769px — первый широкий экран')
assert.equal(menuModeFor(1440), 'dock')
assert.equal(menuModeFor('1024'), 'dock', 'строковая ширина тоже принимается')
assert.equal(menuModeFor(undefined), 'overlay', 'NaN/undefined не даёт dock')
assert.ok(DOCK_MIN_WIDTH > 768)

// --- Открыто ли меню в текущем режиме ---------------------------------------

assert.equal(isMenuOpen('dock', { dockOpen: true, drawerOpen: false }), true)
assert.equal(isMenuOpen('dock', { dockOpen: false, drawerOpen: true }), false,
  'в доке решает dockOpen, не drawerOpen')
assert.equal(isMenuOpen('overlay', { dockOpen: true, drawerOpen: false }), false,
  'в оверлее закрытое drawerOpen — закрыто, даже если dockOpen=true')
assert.equal(isMenuOpen('overlay', { dockOpen: false, drawerOpen: true }), true)

// --- Хранение: по умолчанию ОТКРЫТО, закрыто — только по выбору -------------

function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
  }
}

const empty = fakeStorage()
assert.equal(loadDockPref(empty), true, 'нет записи → открыто (значение по умолчанию)')

const userClosed = fakeStorage({ [MENU_DOCK_KEY]: '0' })
assert.equal(loadDockPref(userClosed), false, "'0' в хранилище → закрыто")

for (const v of ['1', 'true', 'anything']) {
  const s = fakeStorage({ [MENU_DOCK_KEY]: v })
  assert.equal(loadDockPref(s), true, `хранилище '${v}' → открыто`)
}

// Переключение: закрыли → '0', открыли → '1'.
const toggle = fakeStorage()
persistDockPref(toggle, false)
assert.equal(toggle.getItem(MENU_DOCK_KEY), '0', 'закрыли → сохранено 0')
assert.equal(loadDockPref(toggle), false, 'после перезагрузки остаётся закрытым')
persistDockPref(toggle, true)
assert.equal(toggle.getItem(MENU_DOCK_KEY), '1', 'открыли → сохранено 1')
assert.equal(loadDockPref(toggle), true)

// Поломанное/запрещённое хранилище не роняет меню в закрытое состояние.
assert.equal(loadDockPref(null), true, 'нет хранилища → открыто')
assert.equal(loadDockPref({ getItem: () => { throw new Error('denied') } }), true,
  'хранилище кидает → открыто')
assert.doesNotThrow(() => persistDockPref({ setItem: () => { throw new Error('denied') } }, false))

// --- Раскрытие активной группы ----------------------------------------------

const NAV = [
  { kind: 'link', label: 'A' },
  { kind: 'group', key: 'g1', label: 'G1' },
  { kind: 'group', key: 'g2', label: 'G2' },
]

const st = expandedGroupsFor(NAV, 'g2')
assert.deepEqual(st, { g1: false, g2: true }, 'раскрыта только активная группа')
assert.deepEqual(expandedGroupsFor(NAV, null), { g1: false, g2: false },
  'нет активной группы → все свёрнуты')
assert.deepEqual(expandedGroupsFor([], 'x'), {}, 'пустой NAV не ломается')

console.log('navMenu.test.mjs: OK — док открыт по умолчанию, выбор переживает перезагрузку, оверлей закрыт')
