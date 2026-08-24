# 01 — Модель пользователя: роль, статус, тип аккаунта, e-mail

**Type:** task
**Status:** resolved
**Blocked by:** —

**What to build:** `User` перестаёт быть единственным вечным `admin`. Вводятся роль, статус, тип аккаунта, срок жизни, опциональный e-mail, отметка первого входа и последнего логина — на уровне миграции, сущности, `UserDetails` и JWT. Никаких новых экранов; это фундамент для тикетов 02, 03, 04, 06, 08.

## Scope

Миграция `V36__multiuser_user_fields.sql`:

- `role VARCHAR(20) NOT NULL DEFAULT 'USER'` — `USER` | `ADMIN`
- `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` — `ACTIVE` | `BLOCKED`
- `email VARCHAR(255) NULL` + partial unique index `WHERE email IS NOT NULL`
- `onboarding_completed_at TIMESTAMP NULL`
- `last_login_at TIMESTAMP NULL`
- CHECK-констрейнты на допустимые значения двух enum-колонок
- идемпотентный `UPDATE "user" SET role='ADMIN' WHERE username='admin'`
- существующим строкам проставить `onboarding_completed_at = created_at` — старый пользователь не должен попасть в мастер первого входа

**Снято после отмены демо-песочницы** (тикеты 05 и 06 — `wontfix`): колонки `account_type` и `expires_at`, CHECK на `account_type`, индекс `(account_type, expires_at)`. Эфемерных аккаунтов в системе не существует — каждый пользователь постоянный и попадает в систему по инвайт-коду. Если срок жизни аккаунта понадобится позже, он вводится отдельной миграцией вместе со своим сценарием.

## Checklist

- [ ] Миграция применяется на существующей БД без потери данных; повторный прогон не ломается
- [ ] `User` получает поля; `getAuthorities()` возвращает `ROLE_<role>`, а не жёсткий `ROLE_USER`
- [ ] `isEnabled()` возвращает `status == ACTIVE`
- [ ] `JwtUtil` кладёт claim `role`; `JwtAuthenticationFilter` его читает
- [ ] `AuthController.login` обновляет `last_login_at`
- [ ] Заблокированный пользователь получает 401 и на логине, и с валидным токеном
- [ ] `DataInitializer` создаёт `admin` с `role=ADMIN` и выносит сев 9 Сфер жизни в переиспользуемый `LifeSphereSeeder` (нужен тикету 02)
- [ ] Тесты: `@BeforeEach` в `ApiIntegrationTest` пересевает новые поля (Flyway в тестах выключен, `ddl-auto=create-drop`)
- [ ] `admin/admin` продолжает входить после миграции — проверено на реально поднятом контейнере, не только в тесте

## Pitfalls

- Тесты не прогоняют Flyway: любое значение по умолчанию, заданное только в SQL, в тестах не появится. Дублировать дефолты в `@Builder.Default` сущности.
- `LifeSphereSeeder` вынести **до** тикета 02, иначе логика сева 9 сфер будет скопирована в регистрацию и разъедется с `DataInitializer`.
