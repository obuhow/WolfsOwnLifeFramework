# Import/Export (порты): DataSync, ImportXlsx, Delo, DeloImport

Type: task
Status: open
Blocked by: 01

## Question

Мигрировать `DataSyncImportController`, `DataSyncManifestController`, `DataSyncExportController`, `XlsxImportController`, `XlsxImportQuestionController`, `DeloController`, `DeloImportController`. Ключевой вопрос: формализовать ли `Port`/`Adapter` (по аналогии с `note/assistant`) для формата импорта/экспорта, или пока достаточно обычного тонкого Service — сейчас в проекте только один формат хранения (workbook), второй адаптер не существует (см. `codebase-design`: «один адаптер — гипотетический шов»). Существующие `DataSyncImportService`, `DataSyncImportApplyService`, `DataSyncExportService`, `SyncExternalIdService`, `LegacyWorkbookNormalizer`, `DataSyncWorkbookWriter` — решить, как встраиваются в новую структуру без потери уже вынесенной логики (эта фича уже ближе всех к целевому паттерну).

Definition of done: см. map.md; `*ApiIT` (`DataSyncAuthApiIT`, `DataSyncValidationTest`, `LegacyWorkbookNormalizerTest`, `DataSyncContractTest`, `XlsxImportApiIT`, `DeloApiIT`) зелёные без правок.

## Answer

_(заполняется при резолве)_
