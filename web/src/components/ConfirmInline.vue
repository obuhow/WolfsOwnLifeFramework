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
