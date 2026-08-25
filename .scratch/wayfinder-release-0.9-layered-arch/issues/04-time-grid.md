# Time Grid: TimeEntry, Calendar, Routine

Type: task
Status: open
Blocked by: 01

## Question

Мигрировать `TimeEntryController` (847 строк — самый крупный контроллер в проекте, потребует наибольшего внимания), `CalendarController`, `RoutineController`. `RecurrenceService` — уже существующий сервис, решить: поглощается в `RoutineService` или остаётся отдельным (используется правилом повторения, см. CONTEXT.md).

Definition of done: см. map.md; `*ApiIT` (`TimeEntryApiIT`, `CalendarApiIT`, `RoutineApiIT`) зелёные без правок. Обратить особое внимание на `NightHours`/`DayBounds` — доменную логику ночных часов не терять при переносе в Service.

## Answer

_(заполняется при резолве)_
