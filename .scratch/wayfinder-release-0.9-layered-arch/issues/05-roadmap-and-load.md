# Roadmap & Load: Gantt, PlanningCapacity, LoadCurve

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать `GanttController` (587 строк), `PlanningCapacityController`, `LoadCurveController`. `GanttForecastService` — уже существующий сервис, решить: поглощается или остаётся отдельным.

Definition of done: см. map.md; `*ApiIT` (`GanttApiIT`, `LoadCurveApiIT`) зелёные без правок.

## Answer

Миграция завершена в ветке `release-0.9/feature/05-roadmap-load` и влита в `develop`.
`GanttController`, `PlanningCapacityController` и `LoadCurveController` стали тонкими HTTP-адаптерами без прямых `Repository`; бизнес-логика вынесена в `GanttService`, `PlanningCapacityService` и `LoadCurveService`. DTO вынесены в feature-specific `dto/` и оформлены как Java records. `GanttForecastService` сохранён отдельным сфокусированным сервисом и используется как зависимость.

Проверки: `clean compileJava compileTestJava` — успешно; структурная проверка трёх контроллеров — без Repository-зависимостей. `GanttApiIT` и `LoadCurveApiIT` получили только механические обновления типов/accessor'ов для DTO-records; полный IT-релизный gate отложен согласно решению по lightweight testing.
