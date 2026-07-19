# 12 — Гантт: plan/fact, фильтры, режим учёта

**What to build:** Пользователь видит Гантт: дерево Проектов, недели с подписями месяцев, подсветка текущей недели, default старт = понедельник прошлой недели. Редактирует План на неделю на пересечении; видит факт-часы из Записей времени. Фильтры по Областям и «только с сроками». Режим учёта часов (settings) primary_only vs all_projects; ad-hoc не в факте проектов. Полоски, full width.

**Blocked by:** 03 — Настройки; 05 — Проекты; 07 — Записи времени

**Status:** ready-for-agent

- [ ] API week-plan upsert + gantt aggregate query
- [ ] Fact hours respect hour accounting mode + primary
- [ ] Ad-hoc excluded from project fact
- [ ] UI Gantt filters, month headers, current week, strips
- [ ] API-тесты обоих режимов учёта и plan write
