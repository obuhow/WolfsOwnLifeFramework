<script setup>
import { onMounted, ref } from 'vue'
import { apiBase } from './api'

const healthStatus = ref('…')
const healthError = ref('')
const apiBaseLabel = apiBase()

onMounted(async () => {
  try {
    const res = await fetch(`${apiBase()}/health`)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    const body = await res.json()
    healthStatus.value = body.status ?? JSON.stringify(body)
  } catch (e) {
    healthStatus.value = 'недоступен'
    healthError.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <div class="shell">
    <header class="header">
      <div>
        <p class="eyebrow">WOLF</p>
        <h1>Уютно. Тихо. Под вашим контролем.</h1>
      </div>
    </header>

    <main class="main">
      <section class="card">
        <h2>Оболочка 0.1</h2>
        <p>
          Пустой каркас Vue 3. Экраны «Сегодня», «Неделя» и «Гантт» появятся в следующих тикетах.
        </p>
        <dl class="meta">
          <div>
            <dt>API base</dt>
            <dd><code>{{ apiBaseLabel }}</code></dd>
          </div>
          <div>
            <dt>Health</dt>
            <dd>
              <strong :class="healthStatus === 'UP' ? 'ok' : 'bad'">{{ healthStatus }}</strong>
              <span v-if="healthError" class="err"> — {{ healthError }}</span>
            </dd>
          </div>
        </dl>
      </section>
    </main>
  </div>
</template>
