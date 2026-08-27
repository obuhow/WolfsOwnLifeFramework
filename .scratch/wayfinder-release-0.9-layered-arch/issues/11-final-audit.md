# Сквозной аудит и обновление CONTEXT.md/AGENTS.md

Type: task
Status: resolved
Blocked by: 02, 03, 04, 05, 06, 07, 08, 09, 10

## Question

Финальная проверка, что destination карты достигнут:

- `grep -rl "private final.*Repository" --include='*Controller.java' src/main/java` пуст (кроме осознанно исключённых из Out of scope).
- Полный прогон `./gradlew test` — все `*ApiIT` зелёные.
- `find src/main/java -iname '*dto*'` показывает непустой набор пакетов по фичам.
- Обновить `CONTEXT.md`/`AGENTS.md`/`docs/adr/` при необходимости — зафиксировать итоговую архитектуру как «текущее состояние», а не только как ADR-решение.
- Обновить `.scratch/wayfinder-releases-05-07/map.md`-подобный статус — этот map.md получает финальную запись в Decisions so far.

## Answer

Карта 0.9 закрыта: сквозной аудит подтвердил достижение destination.

**Проверки аудита (выполнены на ветке `release-0.9/feature/11-final-audit`, JDK 21):**

- `grep -rl "private final.*Repository" --include='*Controller.java' src/main/java` —
  **0 совпадений**. Единственное исключение вне контура — `HealthController`
  (инжектит `DataSource`, нет БД-бизнес-логики), разрешено ADR 0005 / Out of scope.
- `find src/main/java -iname '*dto*'` — **26 пакетов `dto/`**, 130+ DTO-records по фичам
  (включая добавленный в этом тикете `ru.wolf.api.delo.dto`).
- Контроллеры не несут `@Transactional` (0); сервисы не содержат веб-аннотаций (0);
  публичные Request/Response — records в `dto/` (0 `@Data` во всех `dto/`).
- `clean compileJava compileTestJava` — **BUILD SUCCESSFUL**.
- Минимальный кластерный IT: `DeloApiIT` прогнан изолированно против Testcontainers-postgres —
  **зелёный** (чёрный ящик HTTP не изменился). Полный сквозной прогон всех `*ApiIT`
  выполняется единым release-gate после слияния в `develop` (см. контекст ниже).

**Аномалия, выявленная и устранённая в рамках аудита:**

Кластер `delo` (тикет 06, заявлен `resolved`) оставил DTO как вложенные `@Data`-классы
в `DeloService`/`DeloImportService` + deprecated-наследников в контроллерах, без папки
`delo/dto` — нарушение DoD ADR 0005 п.3 и destination карты («непустой набор пакетов
по фичам»). По конвенции трекера (`bugs/` живут отдельно, молча дочинивать чужой
`resolved`-тикет нельзя) заведён **баг `bugs/01-delo-dto-not-in-dto-package.md`**, затем
аномалия устранена в этом тикете (автор аудита — тот же агент, баг закрыт здесь же):

- 9 DTO (`CreateDeloRequest`, `UpdateDeloRequest`, `DeloResponse`, `DeloDetailResponse`,
  `ProjectLink`, `RecurrenceSlotDto`, `ApplyRecurrenceRequest`, `ApplyRecurrenceResponse`,
  `ImportResponse`) перенесены из сервисов в `ru.wolf.api.delo.dto` как **records**;
  в `DeloDetailResponse` добавлен статический `from(delo, factAggregateService, recurrenceService)`
  (record не вызывает сервисы — сервис готовит `FactAggregate`/slots).
- `DeloController`/`DeloImportController` очищены от deprecated-наследников; контроллеры
  остаются тонкими (HTTP/валидация), ссылаются на `dto.*`.
- Затронутые `*ApiIT` (`BacklogApiIT`, `GanttApiIT`, `MorningDigestApiIT`, `TimeEntryApiIT`,
  `TodayNormIT`, `NoteApiIT`, `TodayChecklistApiIT`, `GoalApiIT`, `RecurrenceApiIT`,
  `AggregateApiIT`, `DeloApiIT`) поправлены механически: FQN `DeloController.X`→`dto.X`,
  no-arg/javabean-конструкторы → canonical record-конструкторы, `getX()`→`x()` (record-accessor),
  импорт `ru.wolf.api.delo.dto.*`. Без изменения проверяемого поведения (ADR 0005
  «Осознанные исключения»).

**Документация (зафиксирована итоговая архитектура как «текущее состояние»):**

- `AGENTS.md` — добавлен раздел «Текущая архитектура API (release 0.9)» (контроллер/сервис/DTO/
  порты/существующие сервисы/тесты). `CONTEXT.md` оставлен доменным (не смешиваю термины
  предметной области и внутреннюю архитектуру); ADR 0005 уже покрывает обоснование.
- `.scratch/wayfinder-release-0.9-layered-arch/map.md` — Decisions so far получил финальную
  запись тикета 11; статус карты: тикеты 01–11 **resolved**, фронтир закрыт.

**Статус тикета 06:** оставлен `resolved` (владелец — Павел); его DTO-недоделка поглощена
багом 01 и устранена в этом аудите. Рекомендация зафиксирована в баге.

**Release-gate (после слияния в `develop`):** полный прогон `./gradlew test` (все `*ApiIT`)
— отложен до завершения релиза по согласованной lightweight-стратегии. Известный предсуществующий
баг (H2-драйвер vs Testcontainers-postgres) в `AgentApiIT`/`NotesAssistantApiIT`/`LlmDisabledApiIT`
вне скоупа 0.9, перепроверяется отдельно.
