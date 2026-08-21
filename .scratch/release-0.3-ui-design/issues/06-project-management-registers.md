# 06 — Управление проектами: реестры, детали, импорт и Настройки

**What to build:** Все уже работающие административные экраны переходят на один flat-register язык: Области жизни, Проекты, Project detail, Дела, Delo detail, CSV‑импорт, Настройки и Login. Доменное поведение не меняется; изменяется только представление, доступность и целостность интерфейса.

**Blocked by:** 01 — Глобальная дизайн-система и базовые контролы; 02 — Навигационный shell: desktop top-bar и mobile drawer.

**Status:** ready-for-agent

- [ ] Области жизни, Проекты и Дела отображаются как ruled registers без плавающих карточек; фильтры и создание размещаются в page header, строки имеют читаемые metadata и текстовые действия.
- [ ] Project detail разделён на factual sections: основная информация, план/факт, дочерние Проекты, связанные Дела, заметки, зависимости/forecast и Synergy‑место для 0.2 без tile‑dashboard.
- [ ] Delo detail разделён на definition, execution mode, Project links, recurrence, aggregate time history и Notes‑место для 0.2; Дело не смешивается с Записью времени в копирайтинге.
- [ ] CSV import остаётся полностью функциональным, использует ruled expandable section, показывает полный schema hint и серверскую row-level ошибку как текст вместо generic visual alert.
- [ ] Settings представляет параметры в fieldsets с правилами и фактическими help texts; Login остаётся минимальной автономной формой без навигации приложения.
- [ ] Все формы/диалоги используют общие lower-rule controls и keyboard/focus behaviour; destructive action требует явного текстового подтверждения.
- [ ] После deploy проверены в браузере авторизованные CRUD/Import страницы и login flow на desktop/mobile без перекрытий или неработающих controls.