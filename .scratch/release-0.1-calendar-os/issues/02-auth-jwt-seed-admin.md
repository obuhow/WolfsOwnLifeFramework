# 02 — Auth JWT + seed admin + изоляция

**What to build:** Пользователь входит по логину/паролю, получает JWT и работает только со своими данными. После bootstrap можно войти как seed `admin`. Второй пользователь никогда не видит сущности первого (проверка на API seam).

**Blocked by:** 01 — Scaffold: API + Vue + Postgres + Compose + test harness

**Status:** ready-for-human

- [x] Login выдаёт JWT; защищённые эндпоинты без токена отклоняются
- [x] Seed Пользователь `admin` создаётся при старте/миграции
- [x] Экран/поток входа на Vue (RU) позволяет войти и сохранить сессию
- [x] API-тест: данные user A недоступны user B
- [x] Logout / очистка клиентского токена работает

## Test Results

See [02-auth-jwt-seed-admin-test-results.md](02-auth-jwt-seed-admin-test-results.md) — all 8 tests passing (HealthApiIT + AuthApiIT).
