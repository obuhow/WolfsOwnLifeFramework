# 05 — Планирование: Диаграмма Ганта в register-стиле

**What to build:** Существующий Гантт становится «Планирование → Диаграмма Ганта»: плоская ruled‑диаграмма с одинаковой геометрией month/week header и проектных строк, нейтральным планом, бледно‑зелёным фактом и понятной суммарной нагрузкой. Пользователь сохраняет inline editing плановых часов и текущие фильтры.

**Blocked by:** 01 — Глобальная дизайн-система и базовые контролы; 02 — Навигационный shell: desktop top-bar и mobile drawer.

**Status:** ready-for-agent

- [ ] Страница и submenu используют утверждённые названия «Планирование» / «Диаграмма Ганта», а старый адрес сохраняет совместимый переход.
- [ ] Gantt header, month spans, week heads, project rows и capacity используют общие explicit CSS Grid tracks; глобальные flex/card стили проектных списков не влияют на Gantt.
- [ ] Sticky project column содержит иерархию, название и metadata; недельные cells показывают plan/fact без насыщенных диаграммных цветов, а факт использует pale green только при наличии completed hours.
- [ ] Inline edit плановых часов остаётся доступным с клавиатуры: Enter сохраняет, Escape отменяет, ошибка/успех сообщаются inline.
- [ ] Capacity находится под теми же week tracks, показывает plan / available и нейтральную textual delta, не блокирует пользователя при превышении.
- [ ] На mobile горизонтальная диаграмма имеет подписанный scroll region и не перекрывается sticky column.
- [ ] После deploy авторизованный DOM‑probe подтверждает, что все week header/cell left и width deltas не превышают 1px.