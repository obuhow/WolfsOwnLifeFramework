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
 * Shared shell for the in-app /docs section (Release 0.5, ticket 02).
 *
 * Narrow column (680-720px), quiet register 0.3 typography: graphite ink,
 * thin neutral rules, no accent colors, no shadowed cards, no progress bars.
 * Available to unauthenticated visitors — /docs is outside the router's
 * auth guard (see main.js).
 *
 * Баг Б-1 (релиз 1.1): под залогиненным пользователем глобальная шапка
 * (App.vue .app-shell) уже рисуется на /docs — вторая шапка раздела лишняя.
 * docs-topbar рендерится только для гостя (без токена).
 */
defineProps({
  active: { type: String, required: true }, // 'manifesto' | 'scenarios' | 'self-hosting'
})

const TABS = [
  { key: 'manifesto', label: 'Манифест WOLF', to: '/docs/manifesto' },
  { key: 'scenarios', label: 'Сценарии использования', to: '/docs/scenarios' },
  { key: 'self-hosting', label: 'Технические инструкции', to: '/docs/self-hosting' },
]

const token = localStorage.getItem('wolf_token') || ''
</script>

<template>
  <div class="docs-shell">
    <header v-if="!token" class="docs-topbar">
      <router-link to="/docs" class="docs-brand">WOLF</router-link>
      <nav class="docs-tabs" aria-label="Разделы документации">
        <router-link
          v-for="tab in TABS"
          :key="tab.key"
          :to="tab.to"
          class="docs-tab"
          :class="{ active: active === tab.key }"
        >{{ tab.label }}</router-link>
      </nav>
    </header>

    <main class="docs-column">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.docs-shell {
  min-height: 100vh;
  background: var(--wolf-surface, #fff);
  color: var(--wolf-ink, #1a1a1a);
}

.docs-topbar {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 16px 28px;
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-brand {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: .14em;
  color: var(--wolf-ink, #1a1a1a);
  text-decoration: none;
}

.docs-tabs {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.docs-tab {
  color: var(--wolf-muted, #737373);
  text-decoration: none;
  font-size: 13px;
  padding: 2px 0;
  border-bottom: 1px solid transparent;
}

.docs-tab:hover,
.docs-tab.active {
  color: var(--wolf-ink, #1a1a1a);
}

.docs-tab.active {
  border-bottom-color: var(--wolf-ink, #1a1a1a);
}

.docs-column {
  max-width: 700px;
  margin: 0 auto;
  padding: 40px 20px 96px;
}

.docs-column :deep(h1) {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -.01em;
  color: var(--wolf-ink, #1a1a1a);
}

.docs-column :deep(.docs-eyebrow) {
  margin: 0 0 28px;
  color: var(--wolf-muted, #737373);
  font-size: 13px;
  line-height: 1.5;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-column :deep(h2) {
  margin: 36px 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
  scroll-margin-top: 20px;
}

.docs-column :deep(h3) {
  margin: 20px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
  scroll-margin-top: 20px;
}

.docs-column :deep(p) {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--wolf-ink, #1a1a1a);
}

.docs-column :deep(ul),
.docs-column :deep(ol) {
  margin: 0 0 14px;
  padding-left: 20px;
  font-size: 14px;
  line-height: 1.6;
}

.docs-column :deep(li) { margin: 4px 0; }

.docs-column :deep(.docs-section) {
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-column :deep(.docs-note) {
  margin: 16px 0 0;
  padding: 12px 14px;
  border-left: 2px solid var(--wolf-rule, #e5e5e5);
  color: var(--wolf-muted, #737373);
  font-size: 13px;
  line-height: 1.55;
}

.docs-column :deep(.docs-toc) {
  margin: 0 0 32px;
  padding: 0 0 20px;
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-column :deep(.docs-toc-title) {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--wolf-faint, #a3a3a3);
}

.docs-column :deep(.docs-toc a) {
  display: block;
  padding: 4px 0;
  font-size: 13px;
  color: var(--wolf-muted, #737373);
  text-decoration: none;
}

.docs-column :deep(.docs-toc a:hover) {
  color: var(--wolf-ink, #1a1a1a);
}

.docs-column :deep(pre) {
  margin: 12px 0;
  padding: 14px 16px;
  background: var(--wolf-subrule, #f2f2f2);
  border: 1px solid var(--wolf-rule, #e5e5e5);
  border-radius: 0;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--wolf-ink, #1a1a1a);
}

.docs-column :deep(pre code) {
  background: none;
  border: 0;
  padding: 0;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  color: inherit;
}

.docs-column :deep(code) {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: .92em;
  background: var(--wolf-subrule, #f2f2f2);
  padding: .1em .35em;
}

.docs-column :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.docs-column :deep(th),
.docs-column :deep(td) {
  text-align: left;
  padding: 6px 10px 6px 0;
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-column :deep(th) {
  color: var(--wolf-muted, #737373);
  font-weight: 600;
}

@media (max-width: 720px) {
  .docs-topbar { padding: 14px 16px; gap: 16px; flex-wrap: wrap; }
  .docs-column { padding: 28px 16px 72px; }
}
</style>
