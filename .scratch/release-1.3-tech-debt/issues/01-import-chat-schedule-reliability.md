# Тикет 01 — Чат-панель «＋»: надёжная материализация расписания и видимый отклик (без тихого no-op)

Status: resolved
Blocked by:
Type: task
Закрывает: `bugs/01-import-chat-recurrence-silent-drop.md` (Б-1)

## Вопрос

Сделать так, чтобы через чат-панель «＋» (`ImportChatPanel.vue`) запрос на вставку
повторяющегося дела **надёжно** попадал в расписание, а когда данных не хватает — система
честно об этом говорила, а не выдавала пустой результат за успех.

## Доказательство контекста

- **Дефект A (тихий no-op):** `ImportParserService.applyConfidenceTable:178` кладёт
  `recurrenceTime=""` при отсутствии времени; `ImportConfirmService.createRecurrence:170`
  применяет правило только при непустом времени → `RecurrenceService.apply` не вызывается,
  но `CreatedEntity(RECURRENCE)` возвращается как успех.
- **Дефект B (сваленные исключения):** `createRecurrence:171-172` —
  `DayOfWeek.valueOf(...)` / `LocalTime.parse(...)` на сырых строках из LLM; плохое значение
  роняет `@Transactional confirm` в HTTP 500 без пояснения (`ImportChatPanel.vue:103`).
- **Контракт полей:** `startAt`/`recurrenceTime` приходят как строки из LLM
  (`ImportParserService` schema `:272,275`, prompt `:300`) — валидность до записи не гарантирована.
- **Переиспользуемый write:** `RecurrenceService.apply(user, deloId, ApplyCommand)` уже умеет
  раскладывать слоты и возвращает `ApplyResult(created, skippedOccupied, skippedPast, ...)`
  (`RecurrenceService.java:60,128`) — есть чем наполнить отклик.
- **Тесты:** happy-path покрыт (`ImportConfirmServiceTest:125,178`); дефектные ветки A и B — нет.

## Что сделать

1. **Убрать тихий no-op для RECURRENCE без времени.** Если у кандидата `RECURRENCE` нет
   валидного времени, `confirm` **не должен** возвращать это как успешно созданное расписание.
   Способ выбрать при работе и обосновать в `## Answer`:
   - вариант A: помечать поле `recurrenceTime` как `NEEDS_CONFIRMATION` (а не пустой
     `CONFIDENT`) уже на разборе, чтобы UI-предпросмотр требовал заполнить его до подтверждения;
   - вариант B: на подтверждении возвращать по этому кандидату явный «не создано: не указано
     время» в ответе (расширить `CreatedEntity`/`ConfirmImportResponse` статусом), UI показывает.
   Предпочтителен A (проблема ловится до записи, в духе «WOLF не додумывает»); при B обосновать.
2. **Устойчивый парс дня/времени в confirm-пути.** Не ронять весь `confirm` из-за одного
   плохого кандидата: заворачивать парс дня/времени, при неуспехе — понятное сообщение
   пользователю (не HTTP 500), остальные валидные кандидаты создаются. Поддержать хотя бы
   формат, который реально шлёт `ImportParserService` (ISO `HH:mm`, англ. `DayOfWeek`); при
   расхождении с фактическими ответами LLM — нормализовать вход.
3. **Видимый отклик о расписании.** Панель `ImportChatPanel.vue` в блоке результата (`:182`)
   должна показывать, **сколько Записей времени реально создано** (проброс `ApplyResult.created`
   / числа слотов из `RecurrenceService.apply` и параллельного слота DELO), а не только список
   сущностей. Оформление по контракту 0.3 — нейтральное, без красного.
4. **Ошибку показывать, тишину — нет.** Любой неуспех подтверждения виден пользователю
   осмысленным текстом (симптом «совсем ничего» недопустим).

## Testing Decisions

- **Дефект A (главный):** кандидат `RECURRENCE` с `recurrenceWeekday=SATURDAY` и пустым
  `recurrenceTime` → confirm НЕ выдаёт это за созданное расписание (вариант A: поле помечено
  NEEDS_CONFIRMATION на разборе; вариант B: в ответе явный статус «не создано»). Регресс-тест
  в `ImportConfirmServiceTest`.
- **Дефект B:** кандидат с некорректным днём/временем → `confirm` не падает 500, валидные
  кандидаты того же запроса создаются, по плохому — понятный статус.
- **Happy-path не сломан:** `RECURRENCE` с `FRIDAY 19:00` по-прежнему зовёт
  `RecurrenceService.apply` и создаёт слоты (существующий тест зелёный).
- **DELO с валидным `startAt`** по-прежнему кладёт параллельный слот (существующий тест зелёный).
- **Отклик:** ответ `import/confirm` несёт число созданных Записей времени; панель его
  показывает.
- **Регресс каналов:** `MaxImportServiceTest` / `TelegramImportServiceTest` зелёные
  (общий `ImportConfirmService`); `npm run build` зелёный.

## Out of Scope

- Переписывание LLM-разбора сверх контракта полей `recurrenceTime`/weekday.
- Новые виды сущностей в чат-панели.
- Изменение модели Записи времени.
- `AgentJob` (заметки-подсказки) — вне объёма 1.3.

## Answer

**П.1 — выбран вариант A** (пометить поле на разборе), как и рекомендовал тикет.
`ImportParserService.applyConfidenceTable` больше не кладёт `recurrenceTime` как
`CONFIDENT ""`; отсутствующее время становится
`NEEDS_CONFIRMATION` со значением `RecurrenceService.DEFAULT_WINDOW_START` (09:00) —
ровно тем же дефолтом, которым пользуется сам write-путь, чтобы предпросмотр и запись не
расходились. Поле приходит в панель как «черновое» (класс `.draft`, уже существовавший),
пользователь его подтверждает или правит **до** записи. Это в духе «WOLF не додумывает»:
проблема ловится до БД, а не объясняется после.

Константа `RecurrenceService.DEFAULT_WINDOW_START` повышена с package-private до `public`
(рядом с уже публичной `MAX_HORIZON_WEEKS`) — иначе пакет `importer` не мог бы переиспользовать
дефолт и завёл бы второй, свой.

**Вариант B не выбран, но частично сделан всё равно** — как страховка: даже если кандидат
с пустым временем придёт из другого канала (Telegram/Max шлют `ConfirmCandidate` напрямую,
минуя предпросмотр панели), `confirm` теперь не выдаёт его за созданное расписание, а
возвращает `timeEntriesCreated=0` + текст «Дело создано, но расписание не заполнено: не
указан день или время повторения». Тихого no-op не остаётся ни на одном входе.

**П.2 — устойчивый парс.** `DayOfWeek.valueOf` / `LocalTime.parse` вынесены в
`parseWeekday` / `parseTime`, которые возвращают `null` вместо исключения; вызов
`recurrenceService.apply` дополнительно обёрнут в `try/catch RuntimeException`. Один плохой
кандидат больше не роняет весь `@Transactional confirm` в HTTP 500 — остальные кандидаты
того же запроса создаются (закреплено тестом
`recurrence_withUnparseableDayOrTime_doesNotAbortTheWholeConfirm`). Сверх контракта
(ISO `HH:mm`, англ. `DayOfWeek`) добавлена нормализация реальных дрейфов LLM: `7.30` → `07:30`,
`9:00` → `09:00`, регистр дня любой.

**П.3 — форма отклика.** `CreatedEntity` расширен двумя полями: `timeEntriesCreated` (сколько
Записей времени реально поставлено) и `note` (короткая причина, когда поставлено не всё).
Наполняются из `RecurrenceService.ApplyResult` (`created` / `skippedOccupied` / `skippedPast`),
а для DELO — из ставшего `int`-овым `placeParallelSlot`. Панель показывает строку вида
«3 записи времени» рядом с сущностью и примечание под ней. Оформление нейтральное по
контракту 0.3: обычный текст в `--muted-foreground`, **без красного, без полос и процентов**.
Конструкторы `CreatedEntity.of(...)` / `.scheduled(...)` добавлены, чтобы Проект/Рутина, которые
ничего не планируют по своей природе, не писали нули руками.

**П.4 — ошибка видна.** Помимо серверной части нашёлся второй источник симптома «совсем
ничего»: в `ImportChatPanel.vue` и `send()`, и `confirmAll()` на `401/403` делали молчаливый
`return` — панель просто ничего не отвечала. Теперь оба бросают понятный текст («Нет доступа…:
войдите заново»), который выводится в существующий баннер ошибки.

**Проверка (Testing Decisions):**

- `ImportConfirmServiceTest` — **12/12 зелёных**, из них 5 новых:
  `recurrence_withoutTime_isNotReportedAsScheduled` (дефект A, главный регресс),
  `recurrence_withUnparseableDayOrTime_doesNotAbortTheWholeConfirm` (дефект B + уцелевший
  валидный кандидат), `recurrence_toleratesSingleDigitHourAndDotSeparator`,
  `delo_withStart_reportsOneTimeEntryCreated`, `delo_withUnparseableStart_reportsNoTimeEntry`.
- Happy-path не сломан: `recurrence_appliesRuleViaRecurrenceService` и
  `delo_withStart_placesParallelTimeEntry` зелёные (первому добавлен стаб `apply`, поскольку
  сервис теперь читает `ApplyResult`).
- Регресс каналов: `TelegramImportServiceTest` 5/5, `MaxImportServiceTest` 8/8.
- `npm run build` зелёный.

Влито в `develop` мержем `506c31c` (ветка `release-1.3/feature/03-admin-role-gate`,
коммит `00ce072` — тикеты 01 и 03 сделаны одной веткой).

**Не сделано осознанно:** ручная приёмка в браузере (создать через «＋» повторяющееся дело
без времени и увидеть черновое поле + счётчик Записей) — за владельцем после передеплоя.
