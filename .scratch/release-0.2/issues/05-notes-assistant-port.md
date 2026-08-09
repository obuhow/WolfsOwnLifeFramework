# 05 — LLM-порт NotesAssistant: fake + аудио-транскрипт + сводка "на чём я остановился"

**What to build:** Внутренний порт `NotesAssistant` (Java interface) с операциями: `transcribe(audioRef) → text`, `summarize(projectId, noteIds) → summary`, `suggest(projectId, topics) → suggestion`. Две реализации: `FakeNotesAssistant` (@Profile("test") или @TestConfiguration, возвращает заготовленные ответы — единственная в тестах) и `HttpNotesAssistant` (реальный HTTP-клиент к xAI Grok, за feature flag `wolf.llm.enabled=false` по умолчанию). Endpoint загрузки аудио → транскрипт → создание Заметки с текстом и ссылкой на аудио. Endpoint `GET /projects/{id}/resume` — сводка последних N заметок проекта через `NotesAssistant.summarize` (ответ "на чём я остановился в X?").

**Blocked by:** 04 — Заметки (нужна модель Заметок).

**Status:** ready-for-agent

- [ ] Interface `NotesAssistant` в domain layer (не инфраструктура)
- [ ] `FakeNotesAssistant` с конфигурируемыми ответами для тестов
- [ ] `HttpNotesAssistant` за feature flag, конфиг через application.yml (url, api-key, model)
- [ ] `POST /api/v1/notes/audio` (multipart) → сохранение файла в volume → транскрипт через порт → Заметка с body=транскрипт и audio_ref
- [ ] `GET /api/v1/projects/{id}/resume?limit=10` — LLM-сводка последних заметок
- [ ] Аудио-файлы: локальный volume или S3-compatible, путь в `note_attachment`
- [ ] Feature flag: при `wolf.llm.enabled=false` endpoints транскрипта и резюме → 503 с пояснением
- [ ] API test: загрузить аудио → заметка создана с транскриптом от fake
- [ ] API test: 3 заметки к проекту → `/resume` возвращает сводку fake, содержащую ключевые слова заметок
- [ ] API test: при выключенном флаге → 503
