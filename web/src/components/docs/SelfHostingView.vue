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
 * Технические инструкции — самохостинг WOLF: docker compose, требования
 * к серверу, шаги развёртывания. По выводам research-тикета «Правовая модель
 * самохостинга: 152-ФЗ и трансграничная передача»
 * (.scratch/wayfinder-releases-05-07/issues/01-personal-data-law-and-selfhosting.md).
 * Код-блоки без цветной подсветки синтаксиса — тихий контракт.
 */
import DocsShell from './DocsShell.vue'
</script>

<template>
  <DocsShell active="self-hosting">
    <h1>Технические инструкции</h1>
    <p class="docs-eyebrow">Шаги для тех, кто рассматривает собственное развёртывание WOLF — не юридическая консультация, честная маркировка того, что известно и что нет.</p>

    <nav class="docs-toc" aria-label="Оглавление">
      <p class="docs-toc-title">На этой странице</p>
      <a href="#legal">Правовая оговорка</a>
      <a href="#requirements">Требования к серверу</a>
      <a href="#compose">docker compose</a>
      <a href="#steps">Шаги развёртывания</a>
    </nav>

    <section id="legal" class="docs-section">
      <h2>Правовая оговорка</h2>
      <p>Закрытая инвайт-модель WOLF — разумная, но не подтверждённая практикой Роскомнадзора трактовка «обработки персональных данных исключительно для личных и семейных нужд» (ст. 1 ч. 2 п. 1 152-ФЗ). Пока круг пользователей — сам владелец и несколько приглашённых близких людей без рекламы и монетизации, риск применения 152-ФЗ низкий, но это оценочное суждение, а не установленный факт.</p>
      <p>При расширении круга пользователей — публичный доступ, реклама, значительное число несвязанных пользователей — обязанности оператора персональных данных применяются в полном объёме, включая требование о локализации базы данных на территории РФ (ст. 18 ч. 5 152-ФЗ). Разворачивая WOLF у себя для более чем узкого личного круга, учитывайте этот риск заранее — это не блокирует установку, но меняет её юридический контекст.</p>
    </section>

    <section id="requirements" class="docs-section">
      <h2>Требования к серверу</h2>
      <ul>
        <li>Linux-хост с Docker и Docker Compose (v2)</li>
        <li>Минимум 2 vCPU, 4 ГБ RAM — Java-бэкенд, Postgres и фронтенд в одном compose-стеке</li>
        <li>10+ ГБ диска на данные Postgres, с запасом под рост Дел/Заметок</li>
        <li>Открытый порт 80 (или собственный reverse proxy с TLS перед контейнером <code>web</code>)</li>
      </ul>
    </section>

    <section id="compose" class="docs-section">
      <h2>docker compose</h2>
      <p>Минимальный набор сервисов — точная копия того, что использует сама WOLF в проде: <code>db</code> (Postgres 16), <code>api</code> (Java 21 / Spring Boot), <code>web</code> (Vue 3 за nginx), <code>docs</code> (эта документация, отдельным контейнером).</p>
      <p class="docs-note">Секреты не пишутся в <code>docker-compose.yml</code>: они берутся из файла <code>.env</code> рядом с ним (шаблон — <code>.env.example</code> в репозитории). Запись <code>${VAR:?…}</code> означает, что без заданной переменной стек намеренно не стартует.</p>
      <pre><code>services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: "${POSTGRES_DB:?не задан}"
      POSTGRES_USER: "${POSTGRES_USER:?не задан}"
      POSTGRES_PASSWORD: "${POSTGRES_PASSWORD:?не задан}"
    volumes:
      - wolf_pgdata:/var/lib/postgresql/data

  api:
    build: ./api
    environment:
      SPRING_DATASOURCE_URL: "jdbc:postgresql://db:5432/${POSTGRES_DB}"
      SPRING_DATASOURCE_USERNAME: "${POSTGRES_USER}"
      SPRING_DATASOURCE_PASSWORD: "${POSTGRES_PASSWORD}"
      WOLF_JWT_SECRET: "${WOLF_JWT_SECRET:?не задан}"
    depends_on:
      - db

  web:
    build: ./web
    ports:
      - "80:80"
    depends_on:
      - api
      - docs

  docs:
    image: nginx:1.27-alpine
    volumes:
      - ./docs-site:/usr/share/nginx/html:ro

volumes:
  wolf_pgdata:</code></pre>
    </section>

    <section id="steps" class="docs-section">
      <h2>Шаги развёртывания</h2>
      <ol>
        <li>Склонировать репозиторий WOLF на сервер.</li>
        <li>Сменить пароли по умолчанию (<code>POSTGRES_PASSWORD</code>, учётные данные администратора) — значения из примера выше не годятся для боевого запуска.</li>
        <li>Запустить стек: <code>docker compose up -d --build</code>.</li>
        <li>Дождаться healthcheck базы данных, затем проверить API: <code>GET /api/actuator/health</code>.</li>
        <li>Открыть приложение на порту 80 — при первом входе создать администратора, дальнейшие пользователи заводятся по инвайт-кодам.</li>
        <li>Настроить резервное копирование тома <code>wolf_pgdata</code> — WOLF не делает это автоматически.</li>
      </ol>
      <p class="docs-note">Готового публичного дистрибутива (образы в реестре, установщик в один клик) пока нет — актуальный способ развернуть WOLF у себя — сборка из исходников по шагам выше.</p>
    </section>
  </DocsShell>
</template>
