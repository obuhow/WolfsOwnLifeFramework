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
 * /docs — вход в раздел Документация. Три подраздела: Манифест, Сценарии
 * использования, Технические инструкции. Доступен без аутентификации.
 */
import DocsShell from './DocsShell.vue'

const LINKS = [
  { to: '/docs/manifesto', label: 'Манифест WOLF', desc: 'Методология, по которой сделан WOLF — пять принципов, каждый с примером из жизни.' },
  { to: '/docs/scenarios', label: 'Сценарии использования', desc: 'Три истории: Рабочий класс, Мудрый фрилансер, Свободный художник.' },
  { to: '/docs/self-hosting', label: 'Технические инструкции', desc: 'Требования к серверу, docker compose, шаги развёртывания у себя.' },
]
</script>

<template>
  <DocsShell active="">
    <h1>Документация</h1>
    <p class="docs-eyebrow">Раздел объясняет WOLF словами, не только интерфейсом — доступен и тем, кто ещё не получил инвайт.</p>

    <section class="docs-section">
      <ul class="docs-index-list">
        <li v-for="link in LINKS" :key="link.to">
          <router-link :to="link.to" class="docs-index-link">{{ link.label }}</router-link>
          <p>{{ link.desc }}</p>
        </li>
      </ul>
    </section>
  </DocsShell>
</template>

<style scoped>
.docs-index-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.docs-index-list li {
  padding: 16px 0;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-index-list li:last-child {
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.docs-index-link {
  display: inline-block;
  font-size: 14px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
  text-decoration: none;
}

.docs-index-link:hover {
  border-bottom: 1px solid var(--wolf-ink, #1a1a1a);
}

.docs-index-list p {
  margin: 6px 0 0;
  color: var(--wolf-muted, #737373);
  font-size: 13px;
  line-height: 1.5;
}
</style>
