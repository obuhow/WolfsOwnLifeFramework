# Разборщик: ImportParserService и контракт уверенности

Status: resolved
Blocked by:

## Question

Реализовать общий сервис разбора свободного текста в кандидаты сущностей WOLF по решениям тикета «Контракт разбора заметки в сущности WOLF» карты `.scratch/wayfinder-releases-05-07/map.md`.

## Что сделать

1. Новый порт `ru.wolf.api.importer.ImportParserService` — независим от `NotesAssistant`, использует тот же LLM HTTP-клиент/конфигурацию (`wolf.llm.*`). Метод `parse(User user, String text) -> ParseResult`, где `ParseResult` — список кандидатов сущностей (Дело/Проект/Рутина/слот расписания), каждое поле каждого кандидата помечено `CONFIDENT` или `NEEDS_CONFIRMATION`.
2. Реализовать таблицу уверенности дословно: правило «час по умолчанию» для длительности без явного маркера; подстановка текущего года пользователя при отсутствии года в дате; горизонт правила повторения ограничен `RecurrenceService.MAX_HORIZON_WEEKS` (12 недель), не материализация 52 записей на год.
3. Ограничить типы создаваемых сущностей: Дела и правила повторения (`RecurrenceService.apply`) — по умолчанию; Проекты и Рутины — только по явному указанию в тексте пользователя.
4. Structured output: контракт запроса включает JSON Schema кандидатов, используется structured output режим HTTP API модели. При невалидном JSON в ответе — одна повторная попытка тем же текстом, при повторном провале — `ParseResult` с флагом `unparsed: true` (не исключение наружу).
5. Занятый слот времени: `ParseResult` содержит найденные конфликты с существующими Записями времени и три опции их разрешения (замена/сдвиг/поверх) — сама логика конфликта, разрешение выбирает вызывающий канал.

## Testing Decisions

- `ImportParserFixtureIT`: фикстуры «после тренировки, часа полтора, потом душ, потом на созвон» и «каждую пятницу в 19:00 английский» — точная проверка уверенности по каждому полю и итогового правила повторения.
- Мок LLM-клиента с невалидным JSON — одна повторная попытка, затем `unparsed: true`, без исключения.
- Занятый слот — `ParseResult` содержит корректный список конфликтов.
- Дело/Проект/Рутина не создаются без явного упоминания в тексте, кроме Дела по умолчанию.

## Out of Scope

Каналы доставки (тикеты 02, 03, 04). Карточка предпросмотра — UI-часть (тикет 02). Материализация записей на год вперёд — явно запрещена, не реализуется.

## Answer

Реализован `ImportParserService` и контракт уверенности (ветка `release-0.7/feature/01-import-parser-service`, от `develop`).

**Слой (по конвенциям 0.9 — thin MVCS, package-by-feature):**
- `ru.wolf.api.importer.ImportParserService` — `@Service`, **без** веб-аннотаций (`@RestController`/`@RequestMapping`/…), резолвит текущего `User` из аргумента, для детекта конфликтов занятых слотов использует `TimeEntryRepository` (прямая инъекция зависимости репозитория в сервис разрешена — запрет касается только контроллеров); LLM-порт — отдельный адаптер. Метод `parse(User, String) -> ParseResult`.
- Порт `ImportParserLlmPort` + адаптеры `HttpImportParserLlmAdapter` (`@Profile("!test")`, тот же `RestClient.Builder`/`NotesAssistantProperties`, structured-output `response_format`) и `FakeImportParserLlmAdapter` (`@Profile("test")` с программной установкой ответа).
- DTO-records в `ru.wolf.api.importer.dto`: `ParseResult`, `ParsedCandidate`, `ParsedField`, `SlotConflict`, `Confidence` (CONFIDENT/NEEDS_CONFIRMATION), `EntityKind` (DELO/PROJECT/ROUTINE/RECURRENCE), `ResolutionOption` (REPLACE/SHIFT/CREATE_OVER), `LlmParseRequest`/`LlmParseResponse`.

**Таблица уверенности (дословно):**
- Правило «час по умолчанию»: нет явного маркера длительности → 60 мин, `NEEDS_CONFIRMATION`.
- Год не указан → подставляется `LocalDate.now(zone).getYear()`, `NEEDS_CONFIRMATION`.
- Типы сущностей: Дело и Повторение — по умолчанию; Проект только при «в проекте X», Рутина только при «как рутину».
- Горизонт повторения ограничен `RecurrenceService.MAX_HORIZON_WEEKS` (расширена видимость до `public`) = 12 недель, не 52.

**Устойчивость:** пустой/нечитаемый текст и дважды невалидный JSON от модели → `ParseResult{unparsed:true, clarificationQuestion}` без исключения наружу; при одном невалидном ответе — одна повторная попытка тем же текстом.

**Занятый слот:** `ParseResult.conflicts` — список `SlotConflict` (индекс кандидата, окно, `existingEntryId`, три опции), само разрешение выбирает вызывающий канал.

**Проверки (минимальные, т.к. полное IT-тестирование — после релиза):**
- `./gradlew compileJava compileTestJava` — BUILD SUCCESSFUL.
- 6 unit-тестов `ImportParserServiceTest` (DB-free, моки порта/репозитория): blank→unparsed; явная/дефолтная длительность и уверенность; Проект только явно; невалидный JSON→повтор+unparsed; горизонт повторения капается на 12. BUILD SUCCESSFUL.
- Структурные: ни один `*Controller` не инжектит Repository; `importer` без веб-аннотаций; 6 record-DTO; адаптеры с `@Profile("!test")`/`@Profile("test")`.

**Полное тестирование (IT-фикстуры примеров А/Б, каналы, лимит) — отложено до завершения релиза 0.7 согласно договорённости.**
