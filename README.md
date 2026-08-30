# WOLF - Wolf's Own Life Framework

Самостоятельная оцифровка своей жизни <br>
для предпринимателей, фрилансеров, художников и разработчиков

> **Статус:** `v0.5 / alpha` · **развёртывание:** self-hosted / Docker · **лицензия:** AGPL-3.0

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
| Web (nginx) | **80** | 80 |
| API | не публикуется | 8080 |
| Postgres | не публикуется | 5432 |
| docs | не публикуется | 80 |

Наружу открыт только веб-контейнер: запросы к API идут через nginx по пути `/api/`,
документация — по `/docs/`. Postgres и API снаружи недоступны, чтобы установка не
оказалась в сети открытой базой. Для локальной отладки в `docker-compose.yml` есть
закомментированные проброски на `127.0.0.1:5434` и `127.0.0.1:8082` — петлевой
интерфейс, не `0.0.0.0`. Порт веб-интерфейса меняется переменной `WEB_HTTP_PORT`.

## Быстрый старт

**Шаг 1. Секреты.** Скопируйте шаблон окружения и подставьте свои значения:

```bash
cp .env.example .env
openssl rand -base64 24   # → POSTGRES_PASSWORD
openssl rand -base64 48   # → WOLF_JWT_SECRET
```

Файл `.env` не попадает в git. Без заданных `POSTGRES_PASSWORD` и `WOLF_JWT_SECRET`
стек намеренно не стартует: значений по умолчанию у них нет, чтобы установка
не оказалась в сети на общеизвестном пароле.

**Шаг 2. Сборка и запуск.** Соберите артефакты, затем runtime-образы:

```bash
cd api && ./gradlew bootJar --no-daemon && cd ..
cd web && npm ci && npm run build && cd ..
docker compose build api web
docker compose up -d --no-build
```

Production workflow собирает образы на VPS внутри Docker. На удалённом хосте не требуются Java, Gradle, Node.js или npm — только Docker Compose и доступ к базовым Docker-образам.

- UI: http://localhost
- Health: http://localhost/api/v1/health

Порты Postgres и API наружу **не публикуются**: контейнеры общаются по внутренней сети
Compose, а запросы к API идут через nginx контейнера `web` по пути `/api/`. Для локальной
отладки в `docker-compose.yml` есть закомментированные проброски на `127.0.0.1`.

> **Публичная установка.** Перед тем как открыть WOLF в интернет, поставьте перед ним
> обратный прокси с TLS и убедитесь, что наружу открыты только 80/443.

## Документация продукта

- Глоссарий: [`CONTEXT.md`](CONTEXT.md)
- ADR: [`docs/adr/`](docs/adr/)
- Конвенции разработки: [`AGENTS.md`](AGENTS.md), [`docs/agents/`](docs/agents/)
- Спеки и тикеты релизов: [`.scratch/`](.scratch/)
