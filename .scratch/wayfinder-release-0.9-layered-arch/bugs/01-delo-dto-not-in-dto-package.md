# Баг 01 — DTO кластера delo не вынесены в `dto/`-пакет (нарушение DoD ADR 0005)

Type: bug
Status: resolved
Найдено при: release-0.9 / тикет 11 (Сквозной аудит)
Подтверждено в релизе 1.0 (сессия 29.08.2026): `ls api/src/main/java/ru/wolf/api/delo/dto/`
показывает 9 record-классов, deprecated-наследники в контроллерах убраны.
Регрессия в: release-0.9 / тикет 06 (Import/Export: DataSync, ImportXlsx, Delo, DeloImport), Status: resolved

## Доказательство расхождения

Аудит карты 0.9 (тикет 11) проверяет destination: «`find src/main/java -iname '*dto*'`
показывает непустой набор пакетов по фичам» и сквозной паттерн ADR 0005
(«публичные Request/Response — records в `dto/`»).

Обнаружено, что пакет `ru.wolf.api.delo` — **единственный** фиче-кластер (кроме
легитимно исключённого `health`), у которого нет папки `dto/`:

```
$ find api/src/main/java -iname '*dto*' -type d | grep delo
(пусто)
$ ls api/src/main/java/ru/wolf/api/delo/
Delo.java  DeloController.java  DeloImportController.java
DeloImportService.java  DeloProject.java  DeloProjectId.java
DeloProjectRepository.java  DeloRepository.java  DeloService.java
```

DTO кластера оформлены НЕ как records в `dto/`, а как вложенные `@Data`-классы
в сервисах, а контроллеры держат deprecated-наследников «для совместимости»:

- `DeloService.java` (строки ~363–468): 8 вложенных `public static @Data` классов —
  `DeloResponse`, `DeloDetailResponse`, `RecurrenceSlotDto`, `ApplyRecurrenceRequest`,
  `ApplyRecurrenceResponse`, `ProjectLink`, `CreateDeloRequest`, `UpdateDeloRequest`.
- `DeloImportService.java` (строки ~414–419): вложенный `public static @Data`
  `ImportResponse`.
- `DeloController.java` (строки ~88–151): 7 deprecated-наследников этих типов
  (`extends DeloService.Xxx`), помеченных `@Deprecated` — «Compatibility DTO names
  retained for existing API integration tests».
- `DeloImportController.java` (строки ~34–39): deprecated `ImportResponse extends
  DeloImportService.ImportResponse`.

Тикет 06 в `Answer` заявляет «публичные DTO вынесены в feature/service DTO records,
с compatibility-типами для существующих compile-time тестов» — то есть DTO фактически
**не вынесены в `dto/` как records**, а оставлены вложенными `@Data`-классами в
сервисе, что противоречит DoD п.3 ADR 0005. Контракт HTTP не нарушен (чёрный ящик
работает), но структурное требование карты не выполнено.

Сквозные проверки аудита, которые ЭТОТ баг роняет:
- `find src/main/java -iname '*dto*'` → непустой набор по фичам: **НЕ выполнено**
  (отсутствует `delo/dto`), хотя map.md (Destination) требует «непустой набор
  пакетов по фичам».
- «публичные Request/Response — records в `dto/`» (ADR 0005, DoD п.3): **НЕ выполнено**
  для delo.

Проверки, которые пройдены (для контекста): `grep -rl "private final.*Repository"
--include='*Controller.java'` пуст (0); контроллеры инжектят сервисы, не Repository;
сервисы без веб-аннотаций; `@Transactional` не в контроллерах.

## Почему это важно

- Нарушает единообразие образца `ru.wolf.api.idea` (ADR 0005), по которому
  мигрированы тикеты 02–10: в каждой другой фиче DTO — records в `<feature>/dto`,
  с маппером `XxxResponse.from(entity)` на самом record. delo — единственное
  исключение, видимое любому агенту, копирующему образец.
- Блокирует финальную проверку аудита (тикет 11): `find -iname '*dto*'` не даёт
  полного покрытия фич, поэтому destination карты формально не достигнут, пока
  delo не приведён к виду records в `dto/`.
- Deprecated-наследники в контроллере — технический долг, который при следующем
  изменении типа DTO придётся править в двух местах (сервис + контроллер).

## Что сделать

1. Создать `ru/wolf/api/delo/dto/` и перенести 8 DTO из `DeloService` + `ImportResponse`
   из `DeloImportService` как Java **records** с валидацией (`@NotBlank`/`@Size` на
   полях request-record'ов) и маппером `XxxResponse.from(...)` на самом record
   (для `DeloDetailResponse` — `from(delo, aggregates, recurrenceWeekdays, slots)`,
   где сервис готовит `FactAggregate`/slots, record не вызывает сервисы).
2. В `DeloService`/`DeloImportService` вернуть record-типы из `dto/`, убрать вложенные
   `@Data`-классы.
3. В `DeloController`/`DeloImportController` убрать deprecated-наследников, сослаться
   на `dto.*`; контроллеры остаются тонкими (только HTTP/валидация).
4. Механически поправить FQN DTO в затронутых `*ApiIT`
   (`BacklogApiIT`, `GanttApiIT`, `MorningDigestApiIT`, `TimeEntryApiIT`,
   `TodayNormIT`, `NoteApiIT`, `TodayChecklistApiIT`, `DeloApiIT`, `RecurrenceApiIT`):
   `ru.wolf.api.delo.DeloController.Xxx` → `ru.wolf.api.delo.dto.Xxx`, no-arg
   конструкторы → canonical с `null`, `getTitle()` → `title()` (record-accessor).
   Без изменения проверяемого поведения (ADR 0005 «Осознанные исключения»).
5. `clean compileJava compileTestJava` и структурный scan зелёные; `delo/dto`
   появляется в `find -iname '*dto*'`.
6. Решить, оставлять ли `resolved` у тикета 06 — владелец тикета 06 (Павел)
   принимает решение; рекомендация аудита: 06 перевести в `resolved` с пометкой
   «DTO доведены до records в dto/ в тикете 11», либо оставить `resolved` и считать
   доработку покрытой багом 01.

## Как закрыт

Баг устранён в рамках тикета 11 (Сквозной аудит): DTO кластера delo перенесены
в `ru/wolf.api.delo.dto` как records, сервисы/контроллеры/тесты поправлены.
Ветка: `release-0.9/feature/11-final-audit`, коммит — см. историю.
Статус тикета 06 оставлен `resolved` владельцем (доработка поглощена аудитом).
