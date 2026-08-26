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
 * Выбор одного из трёх демо-профилей — единственная вёрстка карточек в проекте.
 *
 * Используется дважды (релиз 0.6): экраном `/onboarding/profile` (тикет 02, первый
 * вход) и блоком «Демо-профиль» в Настройках (тикет 05, повторная загрузка).
 * Тикет 05 требует переиспользовать вёрстку, а не дублировать её, поэтому список
 * живёт здесь, а вызывающие экраны отличаются только обработчиком выбора и
 * подписью статуса.
 *
 * Визуальный контракт тихого регистра 0.3: без цвета, теней и скруглений.
 */
defineProps({
  /** slug профиля, по которому идёт запрос; пустая строка — простой. */
  busySlug: { type: String, default: '' },
  /** Подпись под выбранной карточкой на время запроса. */
  busyLabel: { type: String, default: 'Загружаю…' },
})

defineEmits(['select'])

const PROFILES = [
  {
    slug: 'worker-class',
    displayName: 'Рабочий класс',
    description:
      'Наёмный работник, восемь-двенадцать часов в день на основной работе. WOLF помогает ему выкроить свободные часы вокруг смены: ремонт кухни, английский до B2, подготовка к отпуску. Норма — 15 часов в неделю.',
  },
  {
    slug: 'wise-freelancer',
    displayName: 'Мудрый фрилансер',
    description:
      'Пять-шесть проектов разом — коммерческие заказы и один pet-проект, все дерутся за одно и то же время. Он вечно разрывается между клиентами и делает выводы в заметках, чтобы в следующий раз заложить буфер. Норма — 30 часов в неделю.',
  },
  {
    slug: 'free-artist',
    displayName: 'Свободный художник',
    description:
      'Работает рвано, не по графику: недели тишины сменяются плотными сериями. Полный Банк идей — что-то реализовано, что-то заброшено, и это нормально. Норма — 10 часов в неделю.',
  },
]
</script>

<template>
  <ul class="profile-list">
    <li v-for="profile in PROFILES" :key="profile.slug">
      <button
        type="button"
        class="profile-option"
        :data-profile-slug="profile.slug"
        :disabled="!!busySlug"
        :aria-busy="busySlug === profile.slug ? 'true' : 'false'"
        @click="$emit('select', profile.slug)"
      >
        <span class="profile-name">{{ profile.displayName }}</span>
        <span class="profile-desc">{{ profile.description }}</span>
        <span v-if="busySlug === profile.slug" class="profile-status">{{ busyLabel }}</span>
      </button>
    </li>
  </ul>
</template>

<style scoped>
.profile-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--wolf-rule, #e5e5e5);
}

.profile-list li {
  border-bottom: 1px solid var(--wolf-rule, #e5e5e5);
}

.profile-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 20px 4px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.profile-option:hover:not(:disabled) {
  background: var(--wolf-hover, #f7f7f5);
}

.profile-option:focus-visible {
  outline: 2px solid var(--wolf-focus, #1a1a1a);
  outline-offset: -2px;
}

.profile-option:disabled {
  cursor: default;
}

.profile-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--wolf-ink, #1a1a1a);
}

.profile-desc {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--wolf-muted, #737373);
}

.profile-status {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--wolf-faint, #a3a3a3);
}
</style>
