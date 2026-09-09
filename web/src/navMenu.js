/**
 * WOLF — состояние меню навигации (бургер слева).
 *
 * Решение владельца: меню живёт СЛЕВА и по умолчанию ОТКРЫТО.
 *
 * Два режима, зависящих только от ширины экрана:
 *   - 'dock'    (широкие экраны) — колонка в потоке страницы слева от контента.
 *               Раздвигает контент, НЕ затемняет фон, НЕ блокирует скролл и НЕ
 *               перехватывает фокус: это часть страницы, а не модальное окно.
 *               По умолчанию открыта; выбор пользователя переживает перезагрузку.
 *   - 'overlay' (узкие экраны)   — модальная панель поверх контента, по умолчанию
 *               закрыта. Прежнее поведение drawer'а, только выезжает слева.
 *
 * Модуль намеренно чистый (без Vue и без DOM), чтобы поведение можно было
 * проверить тестом — как `durationParse.js`. `App.vue` держит реактивное
 * состояние и вызывает эти функции.
 */

/** Граница режимов, px. Совпадает с media-запросом (max-width: 768px) в style.css. */
export const DOCK_MIN_WIDTH = 769

/** Ключ localStorage: свёрнута ли колонка. '0' — свёрнута, иначе открыта. */
export const MENU_DOCK_KEY = 'wolf_nav_dock_open'

/**
 * Режим меню по ширине окна.
 * @param {number} width
 * @returns {'dock'|'overlay'}
 */
export function menuModeFor(width) {
  return Number(width) >= DOCK_MIN_WIDTH ? 'dock' : 'overlay'
}

/**
 * Открыто ли меню сейчас: в режиме колонки решает dockOpen, в оверлее — drawerOpen.
 * @param {'dock'|'overlay'} mode
 * @param {{dockOpen: boolean, drawerOpen: boolean}} state
 */
export function isMenuOpen(mode, state) {
  return mode === 'dock' ? Boolean(state.dockOpen) : Boolean(state.drawerOpen)
}

/**
 * Читает сохранённый выбор пользователя.
 * По умолчанию — ОТКРЫТО: закрытым меню остаётся, только если пользователь сам
 * свернул его (в хранилище '0'). Отсутствующее и повреждённое значение → открыто.
 * @param {{getItem: (k: string) => string|null}} storage
 */
export function loadDockPref(storage) {
  try {
    return storage?.getItem(MENU_DOCK_KEY) !== '0'
  } catch {
    // Приватный режим браузера может запрещать доступ к хранилищу.
    return true
  }
}

/**
 * Сохраняет выбор пользователя.
 * @param {{setItem: (k: string, v: string) => void}} storage
 * @param {boolean} open
 */
export function persistDockPref(storage, open) {
  try {
    storage?.setItem(MENU_DOCK_KEY, open ? '1' : '0')
  } catch {
    // Недоступное хранилище не должно ломать переключение меню.
  }
}

/**
 * Состояние групп: раскрыта текущая, остальные свёрнуты.
 * @param {Array<{kind: string, key?: string}>} nav
 * @param {string|null} activeKey
 */
export function expandedGroupsFor(nav, activeKey) {
  const state = {}
  for (const item of nav) {
    if (item.kind === 'group') state[item.key] = item.key === activeKey
  }
  return state
}
