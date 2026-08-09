# 04 — Заметки: CRUD + привязка к Проекту/Делу + автор user/agent + теги

**What to build:** Заметки — основа LLM Wiki. Поля: текст (markdown), теги, автор (user | agent), привязка ровно к одному (Проект | Дело) — одна из колонок NULL. Заметки к тренировкам, к Spring Security, к любому Делу. Фильтры: по проекту/делу, по автору, по тегу, поиск по тексту (postgres full-text). UI: список заметок на странице Проекта и Дела, форма создания (textarea + теги), визуальное отличие заметок агента (другой стиль/иконка).

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Таблица `note` (id, user_id, project_id NULL, delo_id NULL, author enum, body, tags[], created_at)
- [ ] Check constraint: ровно одна из (project_id, delo_id) NOT NULL
- [ ] CRUD `/api/v1/notes` с фильтрами (projectId, deloId, author, tag, q)
- [ ] Full-text search по body (pg_trgm или tsvector)
- [ ] UI: блок Заметок на странице Проекта (список + создание)
- [ ] UI: блок Заметок на странице Дела
- [ ] UI: визуальное отличие author=agent (фон, иконка "от агента")
- [ ] API test: создать заметку к проекту и к делу, фильтры по author/tag/q работают
- [ ] API test: заметка без project_id И delo_id → 400
- [ ] API test: поиск "Spring Security" находит заметку по тексту
