<script setup>
/**
 * Project-grouped backlog register (tickets 03, 04, 08).
 * Presentation only — the parent owns loading and mutations.
 */
import { groupHoursLabel, formatHours } from '../backlogGroups'

defineProps({
  groups: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: 'Бэклог пуст.' },
  busy: { type: Boolean, default: false },
  /** Action labels; omit to hide the action. */
  removeLabel: { type: String, default: '' },
  moveLabel: { type: String, default: '' }
})
defineEmits(['remove', 'move'])
</script>

<template>
  <div v-if="loading" class="loading">Загрузка…</div>
  <div v-else-if="error" class="alert alert-error">{{ error }}</div>
  <div v-else-if="groups.length" class="backlog-groups">
    <section v-for="group in groups" :key="group.key" class="backlog-group">
      <header class="backlog-group-head">
        <span class="backlog-group-title">{{ group.label }}</span>
        <span v-if="group.projectId != null" class="backlog-group-hours">{{ groupHoursLabel(group) }}</span>
      </header>
      <ul class="backlog-group-list">
        <li v-for="item in group.items" :key="item.id + '-' + group.key" class="backlog-delo">
          <div class="backlog-delo-body">
            <span class="backlog-delo-title">{{ item.title || item.deloTitle }}</span>
            <span class="backlog-delo-meta">
              <template v-if="item.plannedHours != null">{{ formatHours(item.plannedHours) }} ч</template>
              <template v-if="item.movedToWeek"> · перенесено в {{ item.movedToWeek }}</template>
            </span>
          </div>
          <div class="backlog-delo-actions">
            <button
              v-if="moveLabel && !item.movedToWeek"
              type="button"
              class="btn btn-ghost btn-sm"
              :disabled="busy"
              @click="$emit('move', item)"
            >{{ moveLabel }}</button>
            <button
              v-if="removeLabel"
              type="button"
              class="btn btn-ghost btn-sm"
              :title="removeLabel"
              :disabled="busy"
              @click="$emit('remove', item)"
            >×</button>
          </div>
        </li>
      </ul>
    </section>
  </div>
  <p v-else class="hint">{{ emptyText }}</p>
</template>

<style scoped>
.backlog-groups { display: grid; gap: 18px; }

.backlog-group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--wolf-rule);
}

.backlog-group-title { color: var(--wolf-ink); font-size: 13px; font-weight: 600; }
.backlog-group-hours {
  color: var(--wolf-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.backlog-group-list { list-style: none; margin: 0; padding: 0; }

.backlog-delo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--wolf-subrule);
}

.backlog-delo-body { display: grid; gap: 2px; min-width: 0; }
.backlog-delo-title {
  color: var(--wolf-ink);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.backlog-delo-meta { color: var(--wolf-muted); font-size: 11px; }
.backlog-delo-actions { display: flex; align-items: center; gap: 4px; }
</style>
