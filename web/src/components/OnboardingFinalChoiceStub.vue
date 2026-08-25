<script setup>
/**
 * /onboarding/final — заглушка Финального выбора.
 *
 * Собственно выбор «Оставить / Очистить» и `purgeProfileData` — тикет 04, вне
 * объёма тикета 03. Тур обязан куда-то приводить и при завершении, и при
 * прерывании, поэтому маршрут существует уже сейчас и показывает честное
 * состояние вместо 404. Тикет 04 заменяет этот компонент на
 * `OnboardingFinalChoice.vue`, не трогая выход из тура.
 */
</script>

<template>
  <main class="onboarding-page">
    <section class="onboarding-inner" aria-labelledby="final-choice-title">
      <header class="onboarding-head">
        <p class="eyebrow">WOLF</p>
        <h1 id="final-choice-title">Знакомство завершено</h1>
        <p class="subtitle">
          Выбор «Оставить предзаполненный профиль» или «Очистить профиль» подключается в тикете 04.
          Сейчас загруженные данные остаются на месте.
        </p>
      </header>

      <p class="onboarding-actions">
        <router-link to="/calendar" class="onboarding-link">Перейти в Ежедневник</router-link>
      </p>
    </section>
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

.onboarding-inner {
  width: 100%;
  max-width: 640px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--wolf-faint, #a3a3a3);
}

.onboarding-head h1 {
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.subtitle {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--wolf-muted, #737373);
  max-width: 52ch;
}

.onboarding-actions {
  margin: 28px 0 0;
  padding-top: 20px;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.onboarding-link {
  color: var(--wolf-ink, #1a1a1a);
  font-size: 13px;
  text-decoration: underline;
}
</style>
