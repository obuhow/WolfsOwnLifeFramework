# Daily Rituals & Stats: Checklist, ChecklistReport, Today*, Focus, FocusReview, MorningDigest, WaveStats

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать `ChecklistController`, `ChecklistReportController`, `TodayChecklistController`/`TodayBacklogController` (уточнить точные имена по актуальному дереву), `FocusController`, `FocusReviewController`, `MorningDigestController`, `WaveStatsController`. `MorningDigestService`, `FactAggregateService` — уже существующие сервисы, решить, как встраиваются.

Definition of done: см. map.md; `*ApiIT` (`ChecklistReportApiIT`, `FocusApiIT`, `FocusReviewApiIT`, `TodayChecklistApiIT`, `MorningDigestApiIT`, `WaveStatsApiIT`, `AggregateApiIT`) зелёные без правок.

## Answer

Мигрированы `ChecklistController`, `ChecklistReportController`, `TodayBacklogController`, `FocusController`, `FocusReviewController`, `MorningDigestController` и `WaveStatsController` на thin MVCS по ADR 0005.

- Бизнес-логика вынесена в `ChecklistService`, `ChecklistReportService`, `FocusService`, `FocusReviewService`, `WaveStatsService` и `TodayBacklogService`; существующий `MorningDigestService` сохранён и переведён на DTO-слой.
- Публичные request/response-типы вынесены в feature-specific `dto/` и оформлены как Java `record`; контроллеры не содержат `Repository`.
- Тестовые ссылки на перенесённые DTO обновлены механически, без изменения проверяемого HTTP-поведения.
- Проверки: `GRADLE_OPTS=-Xmx768m ./gradlew clean compileJava compileTestJava --no-daemon --console=plain` — `BUILD SUCCESSFUL`; structural scan — `PASS` (`controllers=7`, `dto_records=28`).
- Полные кластерные `*ApiIT` отложены до единого release-gate 0.9 согласно принятой lightweight стратегии.

_(Реализация: commit `72ac23a`; merge в `develop`: `589fc8b`.)_
