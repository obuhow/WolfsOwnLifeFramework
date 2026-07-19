# 01 — Scaffold: API + Vue + Postgres + Compose + test harness

**What to build:** Разработчик поднимает WOLF локально через Docker Compose и видит живой backend health + пустой Vue shell. Есть один зелёный интеграционный тест через authenticated-ready HTTP API seam на реальном PostgreSQL (Testcontainers), который задаёт стиль тестов для всех следующих тикетов.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Gradle Spring Boot 3 (Java 21) модуль API стартует и отдаёт health
- [ ] Vue 3 SPA scaffold открывается и ходит на API base URL
- [ ] Docker Compose поднимает PostgreSQL (+ согласованный способ гонять API/UI)
- [ ] Один пример API-теста на Testcontainers Postgres зелёный и задокументирован как образец
- [ ] README с командами запуска для человека/агента
