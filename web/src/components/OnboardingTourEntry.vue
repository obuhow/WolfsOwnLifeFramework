<script setup>
/**
 * /onboarding/tour — точка входа в тур Знакомства (релиз 0.6, тикет 03).
 *
 * Собственного интерфейса у неё нет: тур проходит по настоящему меню, поэтому
 * маршрут только включает режим тура и сразу уводит на первый его пункт —
 * «Утренний обход». Дальше работает оверлей `OnboardingTour.vue` в `App.vue`.
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { startTour } from '../onboardingTour'

const router = useRouter()

onMounted(() => {
  startTour({ firstRun: true })
  router.replace('/morning')
})
</script>

<template>
  <main class="onboarding-page">
    <p class="onboarding-note">Открываю Знакомство…</p>
  </main>
</template>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  padding: 64px 24px;
  background: var(--wolf-surface, #ffffff);
}

.onboarding-note {
  margin: 0;
  font-size: 13px;
  color: var(--wolf-muted, #737373);
}
</style>
