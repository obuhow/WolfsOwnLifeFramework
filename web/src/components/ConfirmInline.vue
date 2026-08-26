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
 * Inline textual confirmation for destructive actions (ticket 06).
 * Replaces native confirm() dialogs: the action arms in place and requires a
 * second explicit textual click, with Escape / «Отмена» to back out.
 */
import { nextTick, ref } from 'vue'

const props = defineProps({
  label: { type: String, default: 'Удалить' },
  confirmLabel: { type: String, default: 'Подтвердить удаление' },
  question: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['confirm'])

const armed = ref(false)
const confirmBtn = ref(null)

async function arm() {
  armed.value = true
  await nextTick()
  confirmBtn.value?.focus()
}
function cancel() {
  armed.value = false
}
function accept() {
  armed.value = false
  emit('confirm')
}
</script>

<template>
  <span class="confirm-inline" @keydown.esc.stop.prevent="cancel">
    <button
      v-if="!armed"
      type="button"
      class="btn btn-ghost btn-danger-text"
      :disabled="disabled"
      @click="arm"
    >{{ label }}</button>
    <template v-else>
      <span class="confirm-question">{{ question || 'Точно удалить?' }}</span>
      <button
        ref="confirmBtn"
        type="button"
        class="btn btn-ghost btn-danger-text is-armed"
        :disabled="disabled"
        @click="accept"
      >{{ confirmLabel }}</button>
      <button
        type="button"
        class="btn btn-ghost"
        :disabled="disabled"
        @click="cancel"
      >Отмена</button>
    </template>
  </span>
</template>

<style scoped>
.confirm-inline {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.confirm-question {
  color: var(--wolf-ink);
  font-size: 12px;
}

.btn-danger-text {
  color: var(--wolf-muted);
}

.btn-danger-text:hover:not(:disabled),
.btn-danger-text.is-armed {
  color: var(--wolf-ink);
  text-decoration: underline;
}
</style>
