# 03 — Настройки Пользователя (TZ, ночь, режим часов)

**What to build:** Пользователь читает и меняет свои настройки: timezone (default `Europe/Moscow`), Ночные часы (интервал + видимость), Режим учёта часов (`primary_only` default | `all_projects`). Настройки влияют на последующие календарь/Гантт тикеты.

**Blocked by:** 02 — Auth JWT + seed admin + изоляция

**Status:** ready-for-agent

- [ ] GET/PUT settings для текущего Пользователя
- [ ] Дефолты: Europe/Moscow, primary_only, разумный night interval
- [ ] UI настроек (хотя бы минимальный) на русском
- [ ] API-тесты на defaults и update + изоляция
