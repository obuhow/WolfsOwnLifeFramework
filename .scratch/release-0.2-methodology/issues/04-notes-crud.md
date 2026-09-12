# 04 — Заметки: CRUD + привязка к Проекту/Делу + автор user/agent + теги

**What to build:** Заметки — основа LLM Wiki. Поля: текст (markdown), теги, автор (user | agent), привязка ровно к одному (Проект | Дело) — одна из колонок NULL. Заметки к тренировкам, к Spring Security, к любому Делу. Фильтры: по проекту/делу, по автору, по тегу, поиск по тексту (postgres full-text). UI: список заметок на странице Проекта и Дела, форма создания (textarea + теги), визуальное отличие заметок агента (другой стиль/иконка).

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Таблица `note` (id, user_id, project_id NULL, delo_id NULL, author enum, body, tags[], created_at)
- [x] Check constraint: ровно одна из (project_id, delo_id) NOT NULL
- [x] CRUD `/api/v1/notes` с фильтрами (projectId, deloId, author, tag, q)
- [x] Full-text search по body (pg_trgm или tsvector)
- [x] UI: блок Заметок на странице Проекта (список + создание)
- [x] UI: блок Заметок на странице Дела
- [x] UI: визуальное отличие author=agent (фон, иконка "от агента")
- [x] API test: создать заметку к проекту и к делу, фильтры по author/tag/q работают
- [x] API test: заметка без project_id И delo_id → 400
- [x] API test: поиск "Spring Security" находит заметку по тексту

## Answer

Реализовано в merge commit `9e45ca7` (`merge: notes CRUD ticket 04 into develop`).

Проверки после merge в `develop`:

- `./gradlew test --no-daemon --tests 'ru.wolf.api.note.NoteApiIT'` — 3 теста, 0 failures.
- `npm run build` — успешно.
- `git diff --check` — успешно.

Полный Gradle suite не считается полностью проверенным: Testcontainers завершается с задержкой при остановке тестового PostgreSQL. Браузерная/DOM-проверка после deploy не выполнялась.
