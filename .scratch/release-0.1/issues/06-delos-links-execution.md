# 06 — Дела + связи/primary/способ исполнения + страница Дела

**What to build:** Пользователь ведёт каталог Дел: title, description, Способ исполнения (`self` | `delegatable` | `automatable`), связь с 0..N Проектами и ровно один Основной проект если проектов ≥ 1. Страница Дела позволяет это редактировать; на странице Проекта — attach/detach.

**Blocked by:** 05 — Проекты (дерево) + страница Проекта

**Status:** ready-for-agent

- [ ] CRUD Дело + execution mode
- [ ] Many-to-many Дело–Проект + primary constraint when linked
- [ ] Дело без Проекта валидно
- [ ] Страницы Дела и attach/detach на Проекте
- [ ] API-тесты связей, primary, isolation
