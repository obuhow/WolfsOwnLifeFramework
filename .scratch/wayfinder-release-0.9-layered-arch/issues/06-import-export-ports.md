# Import/Export (порты): DataSync, ImportXlsx, Delo, DeloImport

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать `DataSyncImportController`, `DataSyncManifestController`, `DataSyncExportController`, `XlsxImportController`, `XlsxImportQuestionController`, `DeloController`, `DeloImportController`. Ключевой вопрос: формализовать ли `Port`/`Adapter` (по аналогии с `note/assistant`) для формата импорта/экспорта, или пока достаточно обычного тонкого Service — сейчас в проекте только один формат хранения (workbook), второй адаптер не существует (см. `codebase-design`: «один адаптер — гипотетический шов»). Существующие `DataSyncImportService`, `DataSyncImportApplyService`, `DataSyncExportService`, `SyncExternalIdService`, `LegacyWorkbookNormalizer`, `DataSyncWorkbookWriter` — решить, как встраиваются в новую структуру без потери уже вынесенной логики (эта фича уже ближе всех к целевому паттерну).

Definition of done: см. map.md; `*ApiIT` (`DataSyncAuthApiIT`, `DataSyncValidationTest`, `LegacyWorkbookNormalizerTest`, `DataSyncContractTest`, `XlsxImportApiIT`, `DeloApiIT`) зелёные без правок.

## Answer

Реализована миграция Import/Export в thin MVCS без изменения HTTP-маршрутов и контрактов.

- `DataSyncImportController` и `DataSyncExportController` делегируют orchestration в `DataSyncControllerService`; `DataSyncManifestController` оставлен тонким, поскольку не содержит Repository или бизнес-логики.
- Current-user resolution, проверка формата/версии, preview/get/apply/result/plan/export вынесены из контроллеров в сервисный слой. `ImportXlsxPort` не вводился: для workbook по-прежнему один реальный адаптер.
- `DeloController`, `DeloImportController`, `XlsxImportController` и `XlsxImportQuestionController` очищены от Repository и бизнес-логики; публичные DTO вынесены в feature/service DTO records, с compatibility-типами для существующих compile-time тестов.
- Механически обновлена ссылка на `RecurrenceSlotDto` в `RecurrenceApiIT`; логика проверок не менялась.

Проверки: `GRADLE_OPTS=-Xmx768m ./gradlew clean compileJava compileTestJava --no-daemon --console=plain` — `BUILD SUCCESSFUL` (1m 53s). Structural scan для семи целевых контроллеров: 0 объявлений `private final .*Repository`. Целевые сервисы не содержат web-аннотаций/imports или `ResponseEntity`. Полные `*ApiIT` отложены до release-gate 0.9 согласно принятой lightweight стратегии; отдельные IT в этом тикете не заявляются зелёными.
