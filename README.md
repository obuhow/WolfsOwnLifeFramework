# WOLF - Wolf's One Life Framework

Самостоятельная оцифровка своей жизни <br>
для предпринимателей, фрилансеров, художников и разработчиков

> **Статус:** `v0.1 / alpha` · **развёртывание:** self-hosted / Docker · **лицензия:** open source for personal use (AGPL-3.0)

### Архитектурные принципы

- **Self-hosted:** данные хранятся на личном сервере или VPS пользователя
- **LLM API по выбору:** можно подключать Grok, Claude, GPT или локальные модели

## Технологический стек

Стек 0.1: **Java 21 + Spring Boot 3 + Gradle**, **Vue 3 (Vite)**, **PostgreSQL**, **JWT** (с тикета 02), **Docker Compose**.

## Структура

```
api/                 Spring Boot API (Gradle)
web/                 Vue 3 SPA (Vite)
docker-compose.yml   Postgres + API + web (nginx)
```

## Порты по умолчанию (host)

| Сервис | Host port | Внутри compose |
|--------|-----------|----------------|
| Postgres | **5434** | 5432 |
| API | **8082** | 8080 |
| Web | **80** | 80 |

Порты выбраны так, чтобы не пересекаться с типичным system Postgres `:5432` и другими локальными стеками.

## Быстрый старт

Сначала соберите артефакты на хосте, затем соберите runtime-образы и запустите Compose:

```bash
cd api && ./gradlew bootJar --no-daemon && cd ..
cd web && npm ci && npm run build && cd ..
docker compose build api web
docker compose up -d --no-build
```

Production workflow собирает JAR и frontend на GitHub Actions runner, передаёт только эти артефакты на VPS, а VPS собирает runtime-only образы без Java, Gradle, Node.js и npm.

- UI: http://localhost
- API: http://localhost:8082
- Health: http://localhost:8082/api/v1/health
- Postgres: `localhost:5434`, db/user/pass `wolf` / `wolf` / `wolf`


## Документация продукта

- Глоссарий: [`CONTEXT.md`](CONTEXT.md)
- ADR: [`docs/adr/`](docs/adr/)
- Release 0.1 spec: [`.scratch/release-0.1/spec.md`](.scratch/release-0.1/spec.md)
- Тикеты: [`.scratch/release-0.1/issues/`](.scratch/release-0.1/issues/)
