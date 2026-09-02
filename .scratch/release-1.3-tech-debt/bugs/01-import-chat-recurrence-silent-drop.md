# Б-1 — Чат-панель «＋»: повторяющееся дело не попадает в расписание (тихий no-op parse→confirm)

Type: bug
Status: open
Найдено при: release-1.3-tech-debt (сбор объёма, первый пункт)
Регрессия в: не регрессия — дефект с момента появления канала чат-панели импорта
(release-0.7-ai-import, тикет 0.7-02). Happy-path покрыт тестами, дефектная ветка — нет.

## Симптом

Пользователь открывает чат-панель «＋» (кнопка `import-fab` в `App.vue` → `ImportChatPanel.vue`),
пишет запрос на вставку повторяющегося дела в расписание, подтверждает — **в расписании ничего
не появляется**: Дело может создаться, а Записей времени нет, ошибки нет.

## Как устроен канал (контекст)

- UI: `web/src/components/ImportChatPanel.vue` — `send()` → `POST /api/v1/import/parse`
  (`:64`), `confirmAll()` → `POST /api/v1/import/confirm` (`:97`). Роуты и авторизация в норме
  (`ImportController.java:55,64`, `@AuthenticationPrincipal`).
- Разбор: `ImportParserService.parse` (LLM) → кандидаты с полями и уверенностью.
- Запись: `ImportConfirmService.confirm` — единственный write-path канала; для `RECURRENCE`
  зовёт `RecurrenceService.apply`, для `DELO` с явным `startAt` кладёт параллельный слот.

## Доказательство расхождения

### Дефект A — RECURRENCE с днём недели, но без времени молча теряется

Разбор кладёт `recurrenceTime` **всегда пустой строкой**, если LLM не вернул время, но день
недели есть — `ImportParserService.applyConfidenceTable`:

```
178:  fields.add(ParsedField.confident("recurrenceTime", c.recurrenceTime() == null ? "" : c.recurrenceTime()));
```

А подтверждение применяет правило **только если время непустое** —
`ImportConfirmService.createRecurrence`:

```
170:  if (weekdayRaw != null && !weekdayRaw.isBlank() && timeRaw != null && !timeRaw.isBlank()) {
171:      DayOfWeek weekday = DayOfWeek.valueOf(weekdayRaw.trim().toUpperCase());
172:      LocalTime start = LocalTime.parse(timeRaw.trim());
...
175:      recurrenceService.apply(user, deloId, new RecurrenceService.ApplyCommand(...));
177:  }
178:  return new CreatedEntity(EntityKind.RECURRENCE, deloId, ...);
```

Итог для «каждую субботу тренировка» (день есть, время не названо): `recurrenceTime=""` →
условие `:170` ложно → `apply(...)` **не вызывается**. Дело создаётся, `CreatedEntity`
возвращается как успех, но **ни одной Записи времени не создано и пользователь не уведомлён**,
что время не задано. Ровно «отправил запрос — в расписании ничего».

### Дефект B — ошибки разбора в confirm-пути глотаются/рушат транзакцию без сообщения

`createRecurrence:171-172` вызывает `DayOfWeek.valueOf(...)` и `LocalTime.parse(...)` на
сырых значениях из LLM. Если модель вернёт локализованный день («суббота») или нестандартное
время, парс кинет исключение внутри `@Transactional confirm` — весь `confirm` откатится, а UI
покажет generic `Подтверждение: HTTP 500` (`ImportChatPanel.vue:103`) без пояснения, что не так.
Контракт полей это допускает: `startAt`/`recurrenceTime` приходят из LLM как строки
(`ImportParserService` schema `:272,275`, prompt `:300`), валидность до записи не проверяется.

### Что работает (happy-path, покрыто)

- `DELO` с валидным ISO `startAt` кладёт параллельный слот —
  `ImportConfirmServiceTest.delo_withStart_placesParallelTimeEntry` (`:125`).
- `RECURRENCE` с непустым временем зовёт `RecurrenceService.apply` —
  `recurrence_appliesRuleViaRecurrenceService` (`:178`, время `"19:00"`).
- **Нет теста** на RECURRENCE с пустым/отсутствующим `recurrenceTime` (дефект A) и на
  исключение при плохом дне/времени (дефект B) — дефектная ветка не покрыта.

## Почему это важно

- Основной пользовательский способ «сказать словами и получить в расписании» тихо не работает
  для самого частого случая — повторяющегося дела без явного времени.
- Тихий провал хуже видимой ошибки: пользователь считает, что дело в расписании, а его там нет.
- Нарушен принцип quiet-контракта наоборот: система «додумывает успех», хотя данных не хватило
  (должна была спросить время или честно сказать, что не разложила).

## Что сделать

Реализовать тикетом `issues/01-import-chat-schedule-reliability.md`:
1. Убрать тихий no-op: если у RECURRENCE нет времени — не выдавать это за успех (запросить
   время / вернуть внятный статус, не «создано»).
2. Устойчивый парс дня/времени в confirm-пути с понятным сообщением вместо HTTP 500.
3. Отклик в UI, отражающий, сколько Записей времени реально создано (а не только «создано: Дело»).
4. Тесты на обе дефектные ветки (A и B).

## Как закрыт

Пока не закрыт (`open`). Закрытие — по мерджу тикета 01 релиза 1.3.
