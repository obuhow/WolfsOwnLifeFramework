# 06 — Фоновый агент: scheduled задача + подложки на страницы проектов + ручной триггер

**What to build:** `@Scheduled` задача (cron из конфига, default `0 0 4 * * *` — 4 утра) проходит по активным Проектам пользователя, генерирует подложки через `NotesAssistant.suggest` (ролики по теме, статьи с Хабра, факты — имитируем fake'ом), записывает их как Заметки `author=agent` к соответствующему Проекту. **Никаких уведомлений** — пользователь находит подложки сам при утреннем обходе. `POST /admin/agent/run` для ручного триггера (тесты, отладка). Лог запусков в `agent_run_log` (started_at, finished_at, projects_processed, notes_created, error).

**Blocked by:** 05 — NotesAssistant port (нужен `suggest`).

**Status:** resolved

- [x] `AgentJob` с `@Scheduled`, cron из `application.yml` (`wolf.agent.cron`)
- [x] Выборка активных Проектов (есть хотя бы одна Запись времени за последние 14 дней ИЛИ статус "в работе")
- [x] Генерация подложки через `NotesAssistant.suggest`, запись как Note (author=agent, body от LLM, теги ["agent-suggestion"])
- [x] `agent_run_log` таблица + запись результата каждого запуска
- [x] `POST /api/v1/admin/agent/run` — принудительный запуск (синхронно, для тестов)
- [x] Идемпотентность: повторный запуск за те же сутки не дублирует подложки (check по дате и project_id)
- [x] UI: ничего нового — подложки видны в блоке Заметок Проекта (из 04) со стилем "от агента"
- [x] API test: принудительный запуск → у активного проекта появилась заметка author=agent
- [x] API test: повторный запуск в те же сутки → дубликатов нет
- [x] API test: `agent_run_log` содержит запись о запуске

## Comments

Реализация начата в изолированной ветке `feature/06-background-agent`. UI не менялся: агентские заметки используют существующий Notes UI из тикета 04.

## Verification

- `AgentApiIT` passed on the merged `develop` checkout.
- Existing Notes UI renders agent-authored notes with the agent style.
