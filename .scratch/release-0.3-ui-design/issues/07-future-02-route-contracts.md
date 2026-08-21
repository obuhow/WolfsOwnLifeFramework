# 07 — Контрактные маршруты будущего Release 0.2

**What to build:** До реализации API в навигации и SPA появляются согласованные route-level каркасы будущего UI: Месяц, Бэклог, Цели, Сферы жизни, Синергия, Банк идей, Утренний обход, Заметки / LLM Wiki и Отчёт «Чек-лист». Они дают точные заголовки, структуру, loading/empty states и нейтральную copy, но не подделывают данные и не реализуют бизнес-логику раньше owning tickets.

**Blocked by:** 01 — Глобальная дизайн-система и базовые контролы; 02 — Навигационный shell: desktop top-bar и mobile drawer.

**Status:** ready-for-agent

- [ ] Все утверждённые будущие destinations доступны из правильной navigation group и имеют собственный route/component, без 404 и без fake disabled navigation.
- [ ] Каждый экран содержит утверждённый page title, section anatomy и factual empty/loading state; маршруты не создают Проекты, Дела, Записи времени или фальшивые метрики.
- [ ] Каркас Месяца содержит seven-column month region + future weekly/monthly backlog aside; каркас Планового Бэклога содержит scope/period controls и grouped period sections.
- [ ] Каркасы Целей, Сфер, Синергии, Банка идей, Утреннего обхода, Wiki и отчёта используют закреплённую в spec информацию и нейтральную quiet copy.
- [ ] На desktop и mobile active navigation, drawer behaviour, page heading hierarchy и read order проверены в браузере.
- [ ] Функциональные 0.2 тикеты могут наполнять эти маршруты, не заменяя их визуальный контракт конкурентным дизайном.