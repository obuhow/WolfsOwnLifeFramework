# 01 — Scaffold: API + Vue + Postgres + Compose + test harness

**What to build:** Разработчик поднимает WOLF локально через Docker Compose и видит живой backend health + пустой Vue shell. Есть один зелёный интеграционный тест через authenticated-ready HTTP API seam на реальном PostgreSQL (Testcontainers), который задаёт стиль тестов для всех следующих тикетов.

**Blocked by:** None — can start immediately.

**Status:** in-review

- [x] Gradle Spring Boot 3 (Java 21) модуль API стартует и отдаёт health
- [x] Vue 3 SPA scaffold открывается и ходит на API base URL
- [x] Docker Compose поднимает PostgreSQL (+ согласованный способ гонять API/UI)
- [x] Один пример API-теста на Testcontainers Postgres зелёный и задокументирован как образец
- [x] README с командами запуска для человека/агента
