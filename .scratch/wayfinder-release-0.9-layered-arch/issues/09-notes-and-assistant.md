# Notes & Assistant (формализация существующего порта): Note, NotesAssistant, ProjectResume

Type: task
Status: open
Blocked by: 01

## Question

Мигрировать `NoteController` (тонкий MVCS) и формализовать `note/assistant` под конвенцию портов из тикета 01: `NotesAssistant` уже является портом (`FakeNotesAssistant`/`HttpNotesAssistant` — адаптеры) — привести именование и структуру пакета к общей конвенции проекта (см. map.md: `JpaXxxAdapter`/`HttpXxxAdapter`/`FakeXxxAdapter`), не ломая уже работающий переключатель fake/http (`NotesAssistantConfiguration`, `NotesAssistantProperties`). `ProjectResumeController` — уточнить, зависит ли от `NotesAssistant` или отдельный.

Definition of done: см. map.md; `*ApiIT` (`NotesAssistantApiIT`, `LlmDisabledApiIT`, `NoteApiIT`) зелёные без правок. Эта фича — образец для будущей формализации портов бот-каналов (0.10+, см. Out of scope в map.md), поэтому имена и структура должны быть чистыми.

## Answer

_(заполняется при резолве)_
