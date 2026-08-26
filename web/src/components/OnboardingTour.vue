<!--
  WOLF — Wolf's Own Life Framework
  Copyright (C) 2025 Pavel Obukhov

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->
<script setup>
/**
 * Тур Знакомства (релиз 0.6, тикет 03).
 *
 * Ведущий тур по шести пунктам верхнего уровня NAV из релиза 0.5. Механика:
 * модальное затемнение с вырезом под активный пункт, блокировка кликов вне
 * выреза, текстовая подсказка рядом. Один клик = один пункт меню.
 *
 * Тихий контракт 0.3: без прогресс-бара («шаг 3 из 7»), без цветных
 * индикаторов и иконок — только графитовые правила и текст.
 *
 * Мобильное поведение: пункты верхнего уровня скрыты (drawer). Тур подсвечивает
 * кнопку меню и ждёт, пока drawer откроется, затем подсвечивает нужный пункт
 * внутри drawer — клик по кнопке меню шагом тура не считается.
 *
 * Цели ищутся по атрибуту `data-tour-target` в `App.vue`; DOM меню тур только
 * читает и никогда не меняет.
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { endTour, completeOnboarding, isFirstRunTour } from '../onboardingTour'

const router = useRouter()

// Порядок шагов повторяет порядок NAV в App.vue.
const STEPS = [
  {
    key: 'morning',
    title: 'Утренний обход',
    text: 'С него начинается день: что запланировано на сегодня, что перенеслось со вчера, что просит внимания. Открой раздел, чтобы продолжить.',
  },
  {
    key: 'calendar',
    title: 'Ежедневник',
    text: 'Неделя, месяц и текущий день на одной сетке из пятнадцатиминутных блоков. Здесь виден факт — сколько часов действительно ушло, а не сколько планировалось.',
  },
  {
    key: 'delo-management',
    title: 'Управление делами',
    text: 'Дорожная карта и бэклог, Проекты, Рутины, Дела и Банк идей, статистика и чек-лист. Раскрой раздел, чтобы увидеть, что внутри.',
  },
  {
    key: 'flow',
    title: 'Управление потоком',
    text: 'Области жизни, Цели, Диаграмма компетенций и личная база знаний — то, ради чего расходуются часы из Ежедневника.',
  },
  {
    key: 'docs',
    title: 'Документация',
    text: 'Манифест, сценарии использования и инструкция по самостоятельному размещению. Читается без входа в систему.',
  },
  {
    key: 'settings',
    title: 'Настройки',
    text: 'Недельная норма часов, импорт XLSX и синхронизация данных. Здесь же можно загрузить другой демо-профиль.',
  },
]

const MENU_HINT = {
  title: 'Меню',
  text: 'На узком экране разделы спрятаны в меню. Открой его, чтобы продолжить знакомство.',
}

const PAD = 6
const TOOLTIP_W = 320

const stepIndex = ref(0)
const hole = ref(null) // { top, left, width, height } в координатах вьюпорта
const viaMenu = ref(false) // цель шага недоступна — подсвечена кнопка меню
const settling = ref(false) // короткая пауза после клика, пока DOM перестраивается

const step = computed(() => STEPS[stepIndex.value])
const isLast = computed(() => stepIndex.value === STEPS.length - 1)
const hint = computed(() => (viaMenu.value ? MENU_HINT : step.value))
const exitLabel = computed(() => (isLast.value && !viaMenu.value ? 'Завершить' : 'Пропустить'))

// --- Геометрия ------------------------------------------------------------

function visibleRect(el) {
  if (!el) return null
  const r = el.getBoundingClientRect()
  if (r.width < 1 || r.height < 1) return null
  return r
}

/** Первая реально отрисованная цель с таким ключом (десктоп-шапка или drawer). */
function findTarget(key) {
  const nodes = document.querySelectorAll(`[data-tour-target="${key}"]`)
  for (const node of nodes) {
    if (visibleRect(node)) return node
  }
  return null
}

function union(a, b) {
  const top = Math.min(a.top, b.top)
  const left = Math.min(a.left, b.left)
  return {
    top,
    left,
    width: Math.max(a.right, b.right) - left,
    height: Math.max(a.bottom, b.bottom) - top,
  }
}

/**
 * Вырез вокруг цели. Для раскрытой группы вырез охватывает и выпадающий список,
 * чтобы пользователь видел содержимое раздела, а не только его заголовок.
 */
function holeFor(el) {
  const r = el.getBoundingClientRect()
  let box = { top: r.top, left: r.left, width: r.width, height: r.height, right: r.right, bottom: r.bottom }

  const desktopGroup = el.closest('.nav-group')
  if (desktopGroup) {
    const submenu = desktopGroup.querySelector('.nav-submenu')
    const sub = visibleRect(submenu)
    if (sub) box = withEdges(union(box, sub))
  }

  // В drawer подменю лежит в потоке — рамка группы уже включает раскрытые пункты.
  const drawerGroup = el.closest('.drawer-group')
  if (drawerGroup) {
    const grp = visibleRect(drawerGroup)
    if (grp) box = withEdges(union(box, grp))
  }

  return {
    top: box.top - PAD,
    left: box.left - PAD,
    width: box.width + PAD * 2,
    height: box.height + PAD * 2,
  }
}

function withEdges(b) {
  return { ...b, right: b.left + b.width, bottom: b.top + b.height }
}

function sameHole(a, b) {
  if (!a || !b) return a === b
  return (
    Math.abs(a.top - b.top) < 0.5 &&
    Math.abs(a.left - b.left) < 0.5 &&
    Math.abs(a.width - b.width) < 0.5 &&
    Math.abs(a.height - b.height) < 0.5
  )
}

let rafId = 0
// Ключ цели, для которой уже подкручивали прокрутку, и отметка времени —
// чтобы не бороться с плавной прокруткой на каждом кадре.
let scrolledFor = ''
let scrolledAt = 0

/**
 * Раскрытая группа в drawer сдвигает следующие пункты за нижний край экрана.
 * Подсвечивать невидимую цель бессмысленно — подкручиваем её в кадр.
 *
 * Прокрутка мгновенная: с `behavior: 'smooth'` повторный вызов из следующего
 * кадра перезапускал анимацию до её завершения, и цель так и не доезжала.
 */
function ensureVisible(el, key) {
  const r = el.getBoundingClientRect()
  if (r.top >= 0 && r.bottom <= window.innerHeight) {
    scrolledFor = key
    return
  }
  if (scrolledFor === key && Date.now() - scrolledAt < 250) return
  scrolledFor = key
  scrolledAt = Date.now()
  el.scrollIntoView({ block: 'center' })
}

function measure() {
  let el = findTarget(step.value.key)
  let fallback = false
  if (!el) {
    const trigger = findTarget('menu')
    if (trigger) {
      el = trigger
      fallback = true
    }
  }
  viaMenu.value = fallback
  if (el && !fallback) ensureVisible(el, step.value.key)
  const next = el ? holeFor(el) : null
  if (!sameHole(next, hole.value)) hole.value = next
  rafId = window.requestAnimationFrame(measure)
}

// --- Затемнение (четыре полосы вокруг выреза) ------------------------------

function px(v) {
  return `${Math.round(v)}px`
}

const shades = computed(() => {
  const h = hole.value
  if (!h) return [{ top: '0px', left: '0px', right: '0px', bottom: '0px' }]
  const top = Math.max(0, h.top)
  const left = Math.max(0, h.left)
  const bottom = h.top + h.height
  const right = h.left + h.width
  return [
    { top: '0px', left: '0px', width: '100%', height: px(top) },
    { top: px(top), left: '0px', width: px(left), height: px(bottom - top) },
    { top: px(top), left: px(right), right: '0px', height: px(bottom - top) },
    { top: px(bottom), left: '0px', width: '100%', bottom: '0px' },
  ]
})

const outlineStyle = computed(() => {
  const h = hole.value
  if (!h) return { display: 'none' }
  return { top: px(h.top), left: px(h.left), width: px(h.width), height: px(h.height) }
})

const tooltipStyle = computed(() => {
  const h = hole.value
  if (!h) {
    return { top: '96px', left: '24px', width: px(TOOLTIP_W) }
  }
  const vw = window.innerWidth
  const vh = window.innerHeight
  const width = Math.min(TOOLTIP_W, vw - 32)
  const below = h.top + h.height + 12
  const placeBelow = below + 150 < vh || h.top < 160
  const left = Math.min(Math.max(12, h.left), Math.max(12, vw - width - 12))
  if (placeBelow) return { top: px(below), left: px(left), width: px(width) }
  return { top: px(Math.max(12, h.top - 12 - 150)), left: px(left), width: px(width) }
})

// --- Продвижение по шагам --------------------------------------------------

/**
 * Клик слушается в фазе перехвата: триггеры групп в App.vue гасят всплытие
 * (`@click.stop`), поэтому обычный слушатель на document их не увидел бы.
 */
function onCaptureClick(e) {
  if (settling.value) return
  const target = e.target instanceof Element ? e.target.closest('[data-tour-target]') : null
  if (!target) return
  const key = target.dataset.tourTarget
  // Открытие drawer — не шаг тура: тур ждёт клика по самому пункту внутри него.
  if (key === 'menu') return
  if (key !== step.value.key) return
  advance(target)
}

function advance(el) {
  // Раскрытие группы и переход по маршруту перестраивают DOM — даём ему осесть,
  // и заодно оставляем мгновение, чтобы пользователь увидел раскрытый раздел.
  const isDesktopGroup = !!el.closest('.nav-group')
  const isGroup = isDesktopGroup || !!el.closest('.drawer-group')
  settling.value = true
  window.setTimeout(() => {
    settling.value = false
    // Раскрытый выпадающий список перекрыл бы цель следующего шага. Закрываем
    // его штатным путём оболочки (Escape на document), не трогая DOM меню.
    if (isDesktopGroup) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    }
    if (isLast.value) {
      finish()
    } else {
      stepIndex.value += 1
    }
  }, isGroup ? 1100 : 500)
}

/**
 * Выход из тура — и по завершении последнего шага, и по «Пропустить».
 *
 * Первый вход заканчивается финальным выбором «Оставить»/«Очистить»
 * (релиз 0.6, тикет 04): гость обязан решить судьбу демо-данных сам, и
 * `onboarding_completed_at` проставит уже выбранная им ветка.
 *
 * Повторный запуск из шапки — это просто напоминание по интерфейсу. Показывать
 * там «Очистить» нельзя: пользователь давно живёт в системе, и кнопка удалила
 * бы его настоящие данные. Поэтому знакомство сразу помечается завершённым, а
 * приземление — Ежедневник на вкладке «Неделя», рабочий экран по умолчанию.
 */
async function finish() {
  stopMeasuring()
  const wasFirstRun = isFirstRunTour()
  endTour()
  if (wasFirstRun) {
    router.push('/onboarding/final')
    return
  }
  await completeOnboarding()
  router.push({ path: '/calendar', query: { view: 'week' } })
}

function stopMeasuring() {
  if (rafId) window.cancelAnimationFrame(rafId)
  rafId = 0
  document.removeEventListener('click', onCaptureClick, true)
}

onMounted(() => {
  document.addEventListener('click', onCaptureClick, true)
  measure()
})

onBeforeUnmount(stopMeasuring)
</script>

<template>
  <div class="tour" role="dialog" aria-modal="true" :aria-label="`Знакомство — ${hint.title}`">
    <div
      v-for="(shade, i) in shades"
      :key="i"
      class="tour-shade"
      :style="shade"
      aria-hidden="true"
    />

    <div class="tour-outline" :style="outlineStyle" aria-hidden="true" />

    <div class="tour-hint" :style="tooltipStyle">
      <p class="tour-hint-title">{{ hint.title }}</p>
      <p class="tour-hint-text">{{ hint.text }}</p>
      <button type="button" class="tour-exit" @click="finish">{{ exitLabel }}</button>
    </div>
  </div>
</template>

<style scoped>
.tour {
  position: fixed;
  inset: 0;
  z-index: 900;
  pointer-events: none;
}

/* Затемнение блокирует всё, кроме выреза текущего шага. */
.tour-shade {
  position: fixed;
  background: rgba(26, 26, 26, 0.42);
  pointer-events: auto;
}

.tour-outline {
  position: fixed;
  border: 1px solid var(--wolf-ink, #1a1a1a);
  pointer-events: none;
}

.tour-hint {
  position: fixed;
  z-index: 901;
  padding: 16px;
  border: 1px solid var(--wolf-ink, #1a1a1a);
  border-radius: 0;
  background: var(--wolf-surface, #ffffff);
  box-shadow: none;
  pointer-events: auto;
}

.tour-hint-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.tour-hint-text {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--wolf-muted, #737373);
}

.tour-exit {
  margin-top: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--wolf-ink, #1a1a1a);
  font: inherit;
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.tour-exit:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: 2px;
}
</style>
