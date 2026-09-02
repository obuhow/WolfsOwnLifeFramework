# Б-1 — Агент не вставляет Дела в расписание: нет материализации Записей времени и нет точки входа в UI

Type: bug
Status: open
Найдено при: release-1.3-tech-debt (сбор объёма, первый пункт)
Регрессия в: не регрессия — поведение by design с момента появления фичи `agent`
(release-0.9-layered-arch, feature/10-agent-jobs). Контур агента подтверждён решением
владельца B релиза 1.2 (`.scratch/release-1.2-onboarding-tour/spec.md:57`).

## Симптом

Пользователь отправляет запрос встроенному агенту на вставку некоторых Дел в расписание —
**ничего не происходит**: нет ни сообщения об успехе, ни ошибки, расписание остаётся пустым.

## Доказательство расхождения

Дыр две, они складываются в «совсем ничего».

### 1. Агент не создаёт Записи времени (не материализует расписание)

`api/src/main/java/ru/wolf/api/agent/AgentJob.java` — единственная работа агента для
пользователя это создание заметок-подсказок:

- `runForUserInTransaction` перебирает активные Проекты и для каждого сохраняет `Note`
  автора `AGENT` с тегом `agent-suggestion` (`AgentJob.java:121-128`);
- `TimeEntryRepository` инжектится, но используется **только на чтение** —
  `findOverlapping(...)` в `isActive(...)` для определения «активности» проекта
  (`AgentJob.java:147`). Записи `timeEntryRepository.save(...)` в агенте **нет**:

  ```
  $ grep -n "timeEntryRepository" api/src/main/java/ru/wolf/api/agent/AgentJob.java
  48:    private final TimeEntryRepository timeEntryRepository;
  57:            TimeEntryRepository timeEntryRepository,
  65:        this.timeEntryRepository = timeEntryRepository;
  147:        boolean hasRecentTime = !timeEntryRepository.findOverlapping(userId, from, to)...
  ```

- ответ агента `AgentRunResponse(projectsProcessed, notesCreated, runsLogged)`
  (`agent/dto/AgentRunResponse.java:22`) не содержит ни одного поля про расписание /
  Записи времени.

Материализация расписания живёт в **другой** фиче и агентом не вызывается — единственное
место, где создаётся `TimeEntry` со статусом `PLANNED`, это ручное «Правило повторения»:

```
$ grep -n "timeEntryRepository.save\|PLANNED" api/src/main/java/ru/wolf/api/recurrence/RecurrenceService.java
104:                timeEntryRepository.save(TimeEntry.builder()
109:                        .status(TimeEntry.Status.PLANNED)
```

### 2. В UI нет точки входа для запуска агента

Эндпоинт агента только серверный и admin-scoped:

```
$ grep -n "RequestMapping\|PostMapping" api/src/main/java/ru/wolf/api/agent/AgentController.java
29:@RequestMapping("/api/v1/admin/agent")
35:    @PostMapping("/run")
```

Во фронтенде вызова этого эндпоинта нет вовсе:

```
$ grep -rn "admin/agent\|agent/run" web/src
NONE
```

Штатный запуск агента — по расписанию (`@Scheduled cron ${wolf.agent.cron:0 0 4 * * *}`,
`AgentJob.java:72`). Ручной кнопки «запустить агента» на экране нет, поэтому пользователь
не получает даже ответа `{notesCreated: N}` — отсюда «совсем ничего».

## Почему это важно

- Ожидание пользователя (агент раскладывает Дела по расписанию) прямо расходится с тем, что
  агент делает (заметки-подсказки). Расхождение молчаливое — без ошибки, что хуже видимого сбоя.
- Онбординг-тур 1.2 уже переписан «по факту» под заметки-подсказки (реш. B), то есть контур
  агента был сознательно сужен. Возврат ожидания «агент пишет в расписание» — это **расширение
  контура**, а не тихий фикс, и должно проходить через явное решение владельца.

## Что сделать

1. Реализовать целевое поведение тикетом `issues/01-agent-schedule-materialization.md`
   (агент создаёт Записи времени через `RecurrenceService` + точка входа и отклик в UI).
2. Получить приёмку владельца по **расширению контура агента** относительно реш. B 1.2
   (зафиксировано в `spec.md` → «Важное расхождение» и в `## Answer` тикета 01).
3. Решить, оставлять ли формулировку онбординга 1.2 про «заметки-подсказки» как есть или
   дополнять после расширения контура — статус чужих resolved-тикетов 1.2 меняет владелец.

## Как закрыт

Пока не закрыт (`open`). Закрытие — по мерджу тикета 01 релиза 1.3 после приёмки владельца.
