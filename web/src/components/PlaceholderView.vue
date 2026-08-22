<script setup>
/**
 * Route-contract scaffold for future Release 0.2 screens (ticket 07).
 *
 * Renders the approved page title, section anatomy and a factual empty state
 * from route meta. It deliberately performs NO data fetching and fabricates no
 * metrics: owning functional tickets fill these sections without replacing the
 * visual contract established here.
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const title = computed(() => route.meta?.title || 'Раздел')
const note = computed(() => route.meta?.note || '')
const owner = computed(() => route.meta?.owner || '')
const sections = computed(() => route.meta?.sections || [])
const asides = computed(() => route.meta?.asides || [])
</script>

<template>
  <section class="scaffold-page">
    <header class="page-header">
      <h1>{{ title }}</h1>
      <p v-if="note" class="eyebrow">{{ note }}</p>
    </header>

    <p class="scaffold-contract">
      Структура закреплена дизайн-контрактом Release 0.3. Данные появятся в функциональном тикете
      <template v-if="owner">{{ owner }}</template><template v-else>Release 0.2</template>;
      сейчас раздел ничего не создаёт и не показывает расчётных значений.
    </p>

    <div class="scaffold-layout" :class="{ 'has-aside': asides.length }">
      <div class="scaffold-main">
        <section
          v-for="section in sections"
          :key="section.title"
          class="scaffold-section"
          :aria-label="section.title"
        >
          <header class="scaffold-section-head">
            <h2>{{ section.title }}</h2>
          </header>
          <p v-if="section.description" class="scaffold-section-desc">{{ section.description }}</p>
          <ul v-if="section.fields && section.fields.length" class="scaffold-fields">
            <li v-for="field in section.fields" :key="field">{{ field }}</li>
          </ul>
          <p class="scaffold-empty">{{ section.empty || 'Пока нет данных.' }}</p>
        </section>

        <p v-if="!sections.length" class="scaffold-empty">Раздел в подготовке.</p>
      </div>

      <aside v-if="asides.length" class="scaffold-aside" aria-label="Боковые панели раздела">
        <section v-for="aside in asides" :key="aside.title" class="scaffold-section">
          <header class="scaffold-section-head">
            <h2>{{ aside.title }}</h2>
          </header>
          <p v-if="aside.description" class="scaffold-section-desc">{{ aside.description }}</p>
          <p class="scaffold-empty">{{ aside.empty || 'Пока нет данных.' }}</p>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.scaffold-contract {
  margin: 0 0 20px;
  color: var(--wolf-muted);
  font-size: 13px;
  line-height: 1.5;
  max-width: 68ch;
}

.scaffold-layout {
  display: grid;
  gap: 0;
}

.scaffold-layout.has-aside {
  grid-template-columns: minmax(0, 1fr) 268px;
  gap: 0 24px;
  align-items: start;
}

.scaffold-main {
  display: grid;
  gap: 0;
  min-width: 0;
}

.scaffold-aside {
  display: grid;
  gap: 0;
  min-width: 0;
  border-left: 1px solid var(--wolf-rule);
  padding-left: 24px;
}

.scaffold-section {
  padding: 14px 0 16px;
  border-top: 1px solid var(--wolf-rule);
}

.scaffold-section:last-child {
  border-bottom: 1px solid var(--wolf-rule);
}

.scaffold-section-head h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--wolf-ink);
}

.scaffold-section-desc {
  margin: 6px 0 0;
  color: var(--wolf-muted);
  font-size: 12px;
  line-height: 1.5;
}

.scaffold-fields {
  margin: 8px 0 0;
  padding-left: 18px;
  display: grid;
  gap: 3px;
  color: var(--wolf-muted);
  font-size: 12px;
}

.scaffold-empty {
  margin: 10px 0 0;
  color: var(--wolf-muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .scaffold-layout.has-aside {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .scaffold-aside {
    border-left: 0;
    padding-left: 0;
  }
}
</style>
