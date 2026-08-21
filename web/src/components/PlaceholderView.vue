<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const title = computed(() => route.meta?.title || 'Раздел')
const note = computed(() => route.meta?.note || 'Экран появится в одном из ближайших тикетов Release 0.2.')
</script>

<template>
  <section class="placeholder-page">
    <header class="page-header">
      <h1>{{ title }}</h1>
      <p class="eyebrow">{{ note }}</p>
    </header>

    <div class="placeholder-body">
      <p class="placeholder-line">Раздел в подготовке. Навигация и структура закреплены дизайн-контрактом Release 0.3.</p>
    </div>
  </section>
</template>

<style scoped>
.placeholder-body {
  padding-top: 8px;
}

.placeholder-line {
  margin: 0;
  color: var(--wolf-muted);
  font-size: 13px;
  line-height: 1.5;
}
</style>
