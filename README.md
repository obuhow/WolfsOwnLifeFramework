# WOLF

Самостоятельная оцифровка жизни: одно тёплое место для работы, здоровья, семьи, денег и отдыха.
Слоган: «Уютно. Тихо. Под вашим контролем».

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
| Web | **5174** | 80 |

Порты выбраны так, чтобы не пересекаться с типичным system Postgres `:5432` и другими локальными стеками.

## Быстрый старт (человек / агент)

### Вариант A — Docker Compose (всё сразу)

```bash
docker compose up --build
```

- UI: http://localhost:5174  
- API: http://localhost:8082  
- Health: http://localhost:8082/api/v1/health  
- Postgres: `localhost:5434`, db/user/pass `wolf` / `wolf` / `wolf`

### Вариант B — локальная разработка

1. Поднять только БД:

```bash
docker compose up -d db
```

2. API (default `localhost:5434` + port `8082`):

```bash
cd api
./gradlew bootRun
```

3. SPA (прокси `/api` → API; по умолчанию `http://localhost:8082`):

```bash
cd web
npm install
VITE_DEV_API_PROXY=http://localhost:8082 npm run dev -- --port 5174
```

- UI: http://localhost:5174  
- API base в SPA: `/api/v1` (см. `web/src/api.js`, env `VITE_API_BASE`)

## Тесты (шаблон для следующих тикетов)

Primary seam: **HTTP API** на реальном PostgreSQL через **Testcontainers**.

```bash
cd api
./gradlew test
```

Образец:
- base: `api/src/test/java/ru/wolf/api/support/ApiIntegrationTest.java` (Testcontainers Postgres + `WebTestClient`, hook `authedClient()` под JWT в тикете 02)
- пример: `api/src/test/java/ru/wolf/api/HealthApiIT.java` — HTTP `/api/v1/health` + smoke `select 1` на Postgres

`/api/v1/health` проверяет живой `DataSource` (не «всегда UP»).

Позже: JWT в header, изоляция по Пользователю, сценарии по глоссарию `CONTEXT.md`.

Требование: Docker daemon с API ≥ 1.44 (Docker Engine 25+). В `api/build.gradle` Testcontainers поднят до **1.21.4** из‑за совместимости с современным Docker API.

## Полезные команды

| Действие | Команда |
|----------|---------|
| API tests | `cd api && ./gradlew test` |
| API jar | `cd api && ./gradlew bootJar` |
| FE build | `cd web && npm run build` |
| Compose down + volume | `docker compose down -v` |

## Документация продукта

- Глоссарий: [`CONTEXT.md`](CONTEXT.md)
- ADR: [`docs/adr/`](docs/adr/)
- Release 0.1 spec: [`.scratch/release-0.1/spec.md`](.scratch/release-0.1/spec.md)
- Тикеты: [`.scratch/release-0.1/issues/`](.scratch/release-0.1/issues/)
