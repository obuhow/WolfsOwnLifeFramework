# 09 — Ночные часы + авто-«Сон»

**What to build:** Пользователь настраивает Ночные часы, скрывает/показывает ночные строки сетки. В пустые ночные ячейки подставляется Дело «Сон» (seed на пользователя); ручная другая запись override. Тогл «Показать ночные часы» на Сегодня/Неделе.

**Blocked by:** 03 — Настройки Пользователя; 07 — Запись времени + сетка «Сегодня»

**Status:** ready-for-agent

- [ ] Seed/ensure Дело «Сон» per user
- [ ] Autofill sleep only on empty night cells for the viewed day/week load or explicit ensure endpoint — поведение стабильно и задокументировано
- [ ] Manual entry overrides sleep
- [ ] Hide/show night rows in UI
- [ ] API/UI тесты на autofill и override
