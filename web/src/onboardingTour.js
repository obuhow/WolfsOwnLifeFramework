/**
 * Состояние тура Знакомства (релиз 0.6, тикет 03).
 *
 * Тур — не отдельный экран, а режим поверх обычной оболочки `App.vue`:
 * подсветка идёт по реальным пунктам верхнего уровня NAV, поэтому оболочка
 * должна быть отрисована, а маршрут — меняться как при обычной навигации.
 *
 * Состояние живёт только в памяти вкладки: в БД тур ничего не сохраняет
 * (см. решение тикета — при следующем ручном запуске начинается заново).
 */
import { ref, readonly } from 'vue'

const active = ref(false)

export const tourActive = readonly(active)

export function startTour() {
  active.value = true
}

export function endTour() {
  active.value = false
}

export function isTourActive() {
  return active.value
}
