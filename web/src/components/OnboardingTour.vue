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
 * Приветственный тур (релиз 0.6, тикет 03; сценарный движок — релиз 1.2, тикет 01).
 *
 * Из «обзора меню» (6 шагов, шаг = клик по пункту NAV) движок переработан в
 * СЦЕНАРНЫЙ: шаг завершается одним из четырёх событий (поле `await` шага):
 *
 *   - 'nav'    — клик по узлу с `data-tour-target="<key>"` (пункт верхнего NAV);
 *   - 'click'  — клик по узлу, адресуемому CSS-селектором `target`
 *                (обычно `[data-tour-action="…"]` внутри страницы);
 *   - 'event'  — успешное сохранение сущности: экран после `res.ok` диспатчит
 *                на `document` кастомное событие (`wolf:project-saved` и др.);
 *                движок двигает шаг ТОЛЬКО по факту события, поэтому ошибка
 *                валидации не уводит тур вперёд;
 *   - 'finish' — завершающий шаг: двигается кнопкой «Завершить тур» в подсказке.
 *
 * Массив шагов вынесен в `onboardingSteps.js` (единый источник). 🐺 и счётчик
 * «шаг N из 15» — под ADR-0008 (точечное исключение из тихого контракта 0.3,
 * только внутри узлов оверлея). Прочие правила контракта внутри оверлея в силе:
 * border-radius: 0, box-shadow: none, без сигнальных цветов, графитовая рамка,
 * затемнение rgba(26,26,26,.42).
 *
 * Геометрия выреза, четыре полосы затемнения, ensureVisible(), measure() в
 * rAF-цикле, мобильное поведение через drawer (viaMenu/MENU_HINT) —
 * переиспользованы из 0.6 как есть (через них в 0.6 прошли три исправленных
 * дефекта). Цели ищутся по атрибутам в DOM; DOM меню тур только читает.
 *
 * Устойчивость к перезагрузке: индекс шага и признак активности — в
 * sessionStorage (см. onboardingTour.js). F5 посреди тура возобновляет шаг.
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  endTour,
  completeOnboarding,
  isFirstRunTour,
  loadStepIndex,
  saveStepIndex,
} from '../onboardingTour'
import { STEPS, TOTAL_STEPS } from '../onboardingSteps'

const router = useRouter()

const MENU_HINT = {
  n: null,
  title: '🐺 Меню',
  text: 'На узком экране разделы спрятаны в меню. Открой его, чтобы продолжить знакомство.',
  placement: 'anchored',
}

const PAD = 6
const TOOLTIP_W = 320

// Индекс шага восстанавливается из sessionStorage — перезагрузка посреди тура
// не сбрасывает прогресс (тикет 01 §4). Кламп на случай устаревшего значения.
const stepIndex = ref(Math.min(loadStepIndex(), STEPS.length - 1))
const hole = ref(null) // { top, left, width, height } в координатах вьюпорта
const viaMenu = ref(false) // цель шага недоступна — подсвечена кнопка меню
const settling = ref(false) // короткая пауза после клика, пока DOM перестраивается

const step = computed(() => STEPS[stepIndex.value])
const isLast = computed(() => stepIndex.value === STEPS.length - 1)
const hint = computed(() => (viaMenu.value ? MENU_HINT : step.value))

// Центрированное модальное окно: для шага с placement:'center' — но не когда
// цель ушла в drawer (там подсказка ведёт к кнопке меню).
const isCentered = computed(() => hint.value.placement === 'center' && !viaMenu.value)

// Повторный запуск из шапки (не первый вход): тур ведёт по НАСТОЯЩЕМУ аккаунту.
// Заставлять работающего пользователя создавать проект/дело/рутину = мусорить
// его данными. Поэтому шаги-действия (await:'event') при повторном запуске
// ВЫРОЖДАЮТСЯ в информационные (тикет 09 §4, вариант A): подсказка показывается,
// шаг засчитывается кнопкой «Далее» без требования создать сущность.
const isRepeatRun = !isFirstRunTour()
const isInfoStep = computed(
  () => isRepeatRun && step.value.await === 'event' && !viaMenu.value,
)

// «Завершить тур» — только на финальном шаге (await:'finish'); иначе «Пропустить».
const exitLabel = computed(() =>
  step.value.await === 'finish' && !viaMenu.value ? 'Завершить тур' : 'Пропустить',
)
// Кнопка «Пропустить» на финальном шаге не нужна — там завершение делает
// основная кнопка. На остальных шагах «Пропустить» = досрочный выход.
const showSkip = computed(() => step.value.await !== 'finish' || viaMenu.value)

// --- Разбор цели шага ------------------------------------------------------

/** Селектор выреза (`hole`) текущего шага. null → затемнение целиком. */
function holeSelector(s) {
  if (!s || s.hole === null || s.hole === undefined) return null
  // hole может быть ключом data-tour-target ('morning') или готовым селектором.
  if (s.hole.startsWith('[') || s.hole.startsWith('.') || s.hole.startsWith('#')) return s.hole
  return `[data-tour-target="${s.hole}"]`
}

// --- Геометрия -------------------------------------------------------------

function visibleRect(el) {
  if (!el) return null
  const r = el.getBoundingClientRect()
  if (r.width < 1 || r.height < 1) return null
  return r
}

/** Первый реально отрисованный узел по селектору (десктоп-шапка или drawer). */
function findBySelector(selector) {
  if (!selector) return null
  const nodes = document.querySelectorAll(selector)
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
let scrolledFor = ''
let scrolledAt = 0

/**
 * Раскрытая группа в drawer сдвигает следующие пункты за нижний край экрана.
 * Подсвечивать невидимую цель бессмысленно — подкручиваем её в кадр.
 * Прокрутка мгновенная: 'smooth' перезапускался из следующего кадра и не доезжал.
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
  const s = step.value
  const wantSelector = holeSelector(s)

  // Для шага без выреза (event/finish/center с hole:null) — затемнение целиком.
  if (!wantSelector) {
    viaMenu.value = false
    if (hole.value !== null) hole.value = null
    rafId = window.requestAnimationFrame(measure)
    return
  }

  let el = findBySelector(wantSelector)
  let fallback = false
  // Цель может быть скрыта в drawer на узком экране — ведём к кнопке меню.
  if (!el) {
    const trigger = findBySelector('[data-tour-target="menu"]')
    if (trigger) {
      el = trigger
      fallback = true
    }
  }
  viaMenu.value = fallback
  if (el && !fallback) ensureVisible(el, wantSelector)
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
  // Центрированное окно (приветствие/финал): игнорируем геометрию цели.
  if (isCentered.value) {
    const vw = window.innerWidth
    const width = Math.min(TOOLTIP_W + 40, vw - 32)
    return {
      top: '50%',
      left: '50%',
      width: px(width),
      transform: 'translate(-50%, -50%)',
    }
  }
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
 *
 * nav-шаг засчитывается кликом по `data-tour-target`; click-шаг — кликом по
 * узлу, попадающему под `target`-селектор (обычно `data-tour-action`).
 */
function onCaptureClick(e) {
  if (settling.value) return
  const s = step.value
  const el = e.target instanceof Element ? e.target : null
  if (!el) return

  if (s.await === 'nav') {
    const target = el.closest('[data-tour-target]')
    if (!target) return
    const key = target.dataset.tourTarget
    // Открытие drawer — не шаг тура: ждём клика по самому пункту внутри него.
    if (key === 'menu') return
    if (key !== s.key) return
    advance(target)
  } else if (s.await === 'click') {
    const target = el.closest(s.target)
    if (!target) return
    advance(target)
  }
  // await:'event' и 'finish' по кликам интерфейса не двигаются.
}

/**
 * Событие успешного сохранения сущности (`wolf:project-saved` и др.). Двигает
 * шаг только если это ожидает ТЕКУЩИЙ шаг — иначе тур не должен уезжать.
 */
function onEntitySaved(e) {
  const s = step.value
  if (s.await !== 'event') return
  if (e.type !== s.event) return
  goNext(false)
}

function advance(el) {
  // Раскрытие группы и переход по маршруту перестраивают DOM — даём ему осесть,
  // и оставляем мгновение, чтобы пользователь увидел раскрытый раздел.
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
    goNext(true)
  }, isGroup ? 1100 : 500)
}

/** Переход к следующему шагу или завершение. `fromClick` — для симметрии логов. */
function goNext() {
  if (isLast.value) {
    finish()
    return
  }
  stepIndex.value += 1
  saveStepIndex(stepIndex.value)
}

/**
 * Выход из тура — по кнопке «Завершить тур» на финале и по «Пропустить».
 *
 * Первый вход заканчивается Финальным выбором «Оставить»/«Очистить»
 * (релиз 0.6, тикет 04): гость обязан решить судьбу демо-данных сам, и
 * `onboarding_completed_at` проставит уже выбранная им ветка.
 *
 * Повторный запуск из шапки — напоминание по интерфейсу. Показывать «Очистить»
 * там нельзя: пользователь давно живёт в системе, кнопка удалила бы его
 * настоящие данные. Поэтому знакомство сразу помечается завершённым, а
 * приземление — Ежедневник на вкладке «Неделя».
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
  for (const name of ENTITY_EVENTS) document.removeEventListener(name, onEntitySaved)
}

// События сохранения сущностей, которые движок слушает на document.
const ENTITY_EVENTS = [
  'wolf:project-saved',
  'wolf:delo-saved',
  'wolf:time-entry-saved',
  'wolf:routine-saved',
]

onMounted(() => {
  document.addEventListener('click', onCaptureClick, true)
  for (const name of ENTITY_EVENTS) document.addEventListener(name, onEntitySaved)
  saveStepIndex(stepIndex.value)
  measure()
})

onBeforeUnmount(stopMeasuring)
</script>

<template>
  <div
    class="tour"
    role="dialog"
    aria-modal="true"
    :aria-label="`Приветственный тур — ${hint.title}`"
  >
    <div
      v-for="(shade, i) in shades"
      :key="i"
      class="tour-shade"
      :style="shade"
      aria-hidden="true"
    />

    <div class="tour-outline" :style="outlineStyle" aria-hidden="true" />

    <div class="tour-hint" :class="{ 'tour-hint-center': isCentered }" :style="tooltipStyle">
      <!-- Счётчик «шаг N из 15» — под ADR-0008, только текст, графит, без полосы. -->
      <p v-if="hint.n" class="tour-hint-counter">шаг {{ hint.n }} из {{ TOTAL_STEPS }}</p>
      <p class="tour-hint-title">{{ hint.title }}</p>
      <p class="tour-hint-text">{{ hint.text }}</p>
      <!-- Повторный запуск (тикет 09 §4, вариант A): шаг-действие вырождается в
           информационный — «Далее» вместо ожидания создания сущности. -->
      <button
        v-if="isInfoStep"
        type="button"
        class="tour-finish"
        @click="goNext"
      >
        Далее
      </button>
      <button
        v-if="step.await === 'finish' && !viaMenu"
        type="button"
        class="tour-finish"
        @click="finish"
      >
        Завершить тур
      </button>
      <button
        v-if="showSkip"
        type="button"
        class="tour-exit"
        @click="finish"
      >
        {{ exitLabel }}
      </button>
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

/* Центрированное модальное окно (приветствие/финал). */
.tour-hint-center {
  max-width: calc(100vw - 32px);
}

/* Счётчик «шаг N из 15» — текстовая строка, графит (--wolf-muted), без полосы
   и без процентов (ADR-0008: исключение ограничено текстовой формой). */
.tour-hint-counter {
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.02em;
  color: var(--wolf-muted, #737373);
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

/* Основная кнопка завершения на финальном шаге. Графит, без сигнальных цветов. */
.tour-finish {
  margin-top: 14px;
  padding: 8px 14px;
  border: 1px solid var(--wolf-ink, #1a1a1a);
  border-radius: 0;
  background: var(--wolf-surface, #ffffff);
  color: var(--wolf-ink, #1a1a1a);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.tour-finish:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: 2px;
}

.tour-exit {
  display: block;
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
