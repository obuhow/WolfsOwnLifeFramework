# 03 — Настройки Пользователя (TZ, ночь, режим часов)

**What to build:** Пользователь читает и меняет свои настройки: timezone (default `Europe/Moscow`), Ночные часы (интервал + видимость), Режим учёта часов (`primary_only` default | `all_projects`). Настройки влияют на последующие календарь/Гантт тикеты.

**Blocked by:** 02 — Auth JWT + seed admin + изоляция

**Status:** ready-for-human

- [x] GET/PUT settings для текущего Пользователя
- [x] Дефолты: Europe/Moscow, primary_only, разумный night interval
- [x] UI настроек (хотя бы минимальный) на русском
- [x] API-тесты на defaults и update + изоляция

## Comments

### Test Results (2026-08-10)

All 4 tests in `UserSettingsApiIT` pass:
- `get_settings_returns_defaults_for_seed_admin` ✓
- `update_settings_persists_changes` ✓
- `settings_isolation_between_users` ✓
- `unauthenticated_access_rejected` ✓

Frontend: Added `SettingsView.vue` with form for timezone, night hours (start/end), and hour accounting mode (PRIMARY_ONLY / ALL_PROJECTS). Added `LoginView.vue` and updated `App.vue` with Vue Router for navigation. Build passes.
