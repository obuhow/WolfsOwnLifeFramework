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
  along with this program. if not, see <https://www.gnu.org/licenses/>.
-->
<script setup>
import { ref, reactive, computed } from 'vue'
import { apiBase, authHeaders } from '../api'

// Тихий контракт 0.3: без ярких рамок/иконок предупреждения, поля с нижним
// правилом как в остальном интерфейсе. CONFIDENT-поля — заполнены сразу;
// NEEDS_CONFIRMATION — визуально как черновик (приглушённый текст, не красный).

const text = ref('')
const busy = ref(false)
const error = ref('')
const result = ref(null)      // ParseResult
const confirmed = ref(false)  // после успешного confirm

const kindLabels = {
  DELO: 'Дело',
  PROJECT: 'Проект',
  ROUTINE: 'Рутина',
  RECURRENCE: 'Повторение',
}

// Редактируемые копии полей кандидатов (чтобы пользователь правил до подтверждения).
const edits = reactive({})

function fieldValue(candidate, name) {
  const key = `${candidate._idx}:${name}`
  if (key in edits) return edits[key]
  const f = (candidate.fields || []).find((x) => x.name === name)
  return f ? f.value : ''
}

function setField(candidate, name, value) {
  edits[`${candidate._idx}:${name}`] = value
}

function isConfident(candidate, name) {
  const f = (candidate.fields || []).find((x) => x.name === name)
  return f ? f.confidence === 'CONFIDENT' : false
}

const hasConflicts = computed(() => (result.value?.conflicts || []).length > 0)

async function send() {
  if (!text.value.trim()) { error.value = 'Введите текст'; return }
  busy.value = true; error.value = ''; confirmed.value = false
  try {
    const res = await fetch(`${apiBase()}/import/parse`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ text: text.value }),
    })
    if (res.status === 401 || res.status === 403) return
    if (!res.ok) throw new Error(`Разбор: HTTP ${res.status}`)
    const data = await res.json()
    if (data.unparsed) {
      result.value = { unparsed: true, clarificationQuestion: data.clarificationQuestion }
    } else {
      (data.candidates || []).forEach((c, i) => { c._idx = i })
      result.value = data
    }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function confirmAll() {
  if (!result.value || result.value.unparsed) return
  busy.value = true; error.value = ''
  try {
    const candidates = (result.value.candidates || []).map((c) => ({
      kind: c.kind,
      fields: (c.fields || []).map((f) => ({
        name: f.name,
        value: fieldValue(c, f.name),
        confidence: f.confidence,
      })),
    }))
    const res = await fetch(`${apiBase()}/import/confirm`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ candidates }),
    })
    if (res.status === 401 || res.status === 403) return
    if (!res.ok) throw new Error(`Подтверждение: HTTP ${res.status}`)
    const data = await res.json()
    confirmed.value = true
    result.value = { created: data.created || [] }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function reset() {
  text.value = ''
  result.value = null
  confirmed.value = false
  error.value = ''
  for (const k in edits) delete edits[k]
}
</script>

<template>
  <section class="import-panel" aria-label="Импорт записей">
    <header class="panel-head">
      <h2>Импорт записей</h2>
      <p class="eyebrow">Свободный текст → Дела, Проекты, Рутины</p>
    </header>

    <!-- Ввод -->
    <div v-if="!result" class="input-row">
      <textarea
        v-model="text"
        class="chat-input"
        rows="3"
        placeholder="Напишите, что нужно сделать — например: «после тренировки, часа полтора, потом душ»"
        @keydown.ctrl.enter="send"
      ></textarea>
      <button class="btn btn-primary" :disabled="busy || !text.trim()" @click="send">
        {{ busy ? 'Разбор…' : 'Разобрать' }}
      </button>
    </div>

    <p v-if="error" class="banner error">{{ error }}</p>

    <!-- Не разобралось: один уточняющий вопрос текстом, без создания сущностей -->
    <div v-if="result && result.unparsed" class="clarify">
      <p class="clarify-text">{{ result.clarificationQuestion }}</p>
      <button class="btn btn-ghost" @click="reset">Написать снова</button>
    </div>

    <!-- Карточка предпросмотра: одна общая для всех кандидатов -->
    <div v-if="result && !result.unparsed && !confirmed" class="preview">
      <div v-for="c in result.candidates" :key="c._idx" class="candidate-card">
        <div class="candidate-kind">{{ kindLabels[c.kind] || c.kind }}</div>
        <div v-for="f in c.fields" :key="f.name" class="field-row" :class="{ draft: !isConfident(c, f.name) }">
          <label :for="`f-${c._idx}-${f.name}`" class="field-label">{{ f.name }}</label>
          <input
            :id="`f-${c._idx}-${f.name}`"
            class="field-input"
            :class="{ confident: isConfident(c, f.name) }"
            :value="fieldValue(c, f.name)"
            @input="setField(c, f.name, $event.target.value)"
          />
        </div>
      </div>

      <!-- Занятый слот: три варианта прямо в карточке (всегда Создать поверх) -->
      <p v-if="hasConflicts" class="conflict-note">
        Слот пересекается с существующей записью — будет создан параллельный интервал (поверх).
      </p>

      <div class="preview-actions">
        <button class="btn btn-primary" :disabled="busy" @click="confirmAll">
          {{ busy ? 'Запись…' : 'Подтвердить' }}
        </button>
        <button class="btn btn-ghost" @click="reset">Отмена</button>
      </div>
    </div>

    <!-- Результат подтверждения -->
    <div v-if="confirmed" class="done">
      <ul class="created-list">
        <li v-for="e in result.created" :key="`${e.type}-${e.id}`">
          <span class="created-kind">{{ kindLabels[e.kind] || e.kind }}</span>
          <a :href="`#${e.link}`" class="created-link">{{ e.title }}</a>
        </li>
      </ul>
      <button class="btn btn-ghost" @click="reset">Ещё запись</button>
    </div>
  </section>
</template>

<style scoped>
.import-panel {
  display: grid;
  gap: 1rem;
  background: var(--card, #fffdf9);
  border: 1px solid var(--border, #e6dfd4);
  border-radius: 10px;
  padding: 1.1rem 1.2rem;
  max-width: 42rem;
}
.panel-head h2 { margin: 0; font-size: 1.05rem; font-weight: 600; }
.eyebrow { margin: 0.15rem 0 0; color: var(--muted-foreground, #756d64); font-size: 0.82rem; }

.input-row { display: grid; gap: 0.6rem; }
.chat-input {
  width: 100%;
  resize: vertical;
  font: inherit;
  padding: 0.6rem 0.7rem;
  border: 1px solid var(--border, #e6dfd4);
  border-bottom: 2px solid var(--border, #e6dfd4);
  border-radius: 8px;
  background: var(--background, #fff);
  color: var(--foreground, #2b2620);
}
.chat-input:focus { outline: none; border-bottom-color: var(--accent, #9a7b4f); }

.btn { font: inherit; cursor: pointer; border-radius: 8px; padding: 0.45rem 0.9rem; border: 1px solid transparent; }
.btn-primary { background: var(--accent, #9a7b4f); color: #fff; }
.btn-primary:disabled { opacity: 0.55; cursor: default; }
.btn-ghost { background: transparent; color: var(--muted-foreground, #756d64); border-color: var(--border, #e6dfd4); }

.banner { margin: 0; padding: 0.5rem 0.7rem; border-radius: 8px; font-size: 0.88rem; }
.banner.error { background: #fbeae9; color: #8a3632; border: 1px solid #eccfcc; }

.clarify { display: grid; gap: 0.7rem; }
.clarify-text { margin: 0; color: var(--foreground, #2b2620); font-size: 0.95rem; }

.preview { display: grid; gap: 0.8rem; }
.candidate-card {
  border: 1px solid var(--border, #e6dfd4);
  border-radius: 8px;
  padding: 0.7rem 0.8rem;
  display: grid;
  gap: 0.5rem;
}
.candidate-kind { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted-foreground, #756d64); }
.field-row { display: grid; grid-template-columns: 9rem 1fr; align-items: center; gap: 0.5rem; }
.field-label { color: var(--muted-foreground, #756d64); font-size: 0.84rem; }
.field-input {
  font: inherit;
  padding: 0.32rem 0.5rem;
  border: 1px solid var(--border, #e6dfd4);
  border-bottom: 2px solid var(--border, #e6dfd4);
  border-radius: 6px;
  background: var(--background, #fff);
  color: var(--foreground, #2b2620);
}
/* NEEDS_CONFIRMATION — визуально как черновик (приглушённый текст), не красный */
.field-row.draft .field-input { color: var(--muted-foreground, #756d64); font-style: italic; }
.field-input:focus { outline: none; border-bottom-color: var(--accent, #9a7b4f); }

.conflict-note { margin: 0; font-size: 0.84rem; color: var(--muted-foreground, #756d64); }
.preview-actions { display: flex; gap: 0.6rem; }

.done { display: grid; gap: 0.7rem; }
.created-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.35rem; }
.created-kind { font-size: 0.74rem; text-transform: uppercase; color: var(--muted-foreground, #756d64); margin-right: 0.4rem; }
.created-link { color: var(--accent, #9a7b4f); text-decoration: none; }
.created-link:hover { text-decoration: underline; }
</style>
