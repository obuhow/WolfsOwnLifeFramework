# 12 — Персональный админский аккаунт obuhov

**Type:** task
**Status:** resolved
**Blocked by:** 01 — Модель пользователя (роль/статус готовы)

**What to build:** Именной администраторский аккаунт `obuhov` с ролью `ADMIN`, которым владелец системы реально пользуется для проверки и выпуска инвайт-кодов — вместо общего преднастроенного `admin/admin`. Это подготовительный шаг к тикету 13 (закрытие доступа к seed-аккаунту `admin`): нельзя закрывать единственный вход в систему, не создав рабочую замену и не проверив её.

## Scope

- Миграция `V38__admin_obuhov.sql`: идемпотентно создаёт пользователя `obuhov` (`INSERT ... WHERE NOT EXISTS`) с `role='ADMIN'`, `status='ACTIVE'`, `account_type='REGULAR'`, `onboarding_completed_at = now()` (админ не должен попадать в мастер первого входа — как и текущий `admin`, см. тикет 08 и `DataInitializer`).
- Пароль задаётся **не** хардкодом в миграции. Варианты (выбрать один и явно задокументировать в PR/тикете):
  - Временный пароль через существующий `POST /api/v1/admin/users/{id}/reset-password` сразу после применения миграции (создать пользователя без осмысленного пароля-плейсхолдера, залогиниться под текущим `admin`, вызвать reset, получить временный пароль один раз);
  - либо переменная окружения, читаемая `DataInitializer`-подобным `CommandLineRunner` при первом старте (аналогично текущему сидингу `admin`, но конфигурируемо, а не хардкод `admin/admin`).
  Не класть пароль в SQL-миграцию открытым текстом и не коммитить его в репозиторий.
- Засеять `obuhov` теми же 9 Сферами жизни через существующий `LifeSphereSeeder` (как для любого нового пользователя).
- Проверка «последнего активного админа» (`AdminController.deleteUser`, `countByRoleAndStatus("ADMIN","ACTIVE")`) уже существует и защитит от одновременного отсутствия обоих админов — использовать её при последующем отключении `admin` в тикете 13, отдельного кода здесь не требуется.

## Checklist

- [x] Миграция создаёт `obuhov` идемпотентно (повторный прогон не дублирует и не падает)
- [x] `obuhov` имеет `role=ADMIN`, `status=ACTIVE`, `onboarding_completed_at` проставлен
- [x] `obuhov` засеян 9 Сферами жизни
- [x] Пароль выдан владельцу вне репозитория (через reset-password эндпоинт или переменную окружения), нигде не закоммичен открытым текстом
- [x] Владелец подтверждает успешный логин под `obuhov` и успешный выпуск инвайт-кода (`POST /api/v1/admin/invite-codes`) до перехода к тикету 13
- [x] На момент закрытия тикета в системе есть минимум два активных ADMIN (`admin` и `obuhov`) — тикет 13 закрывает доступ к первому только после этой проверки

## Pitfalls

- Не удалять и не блокировать `admin` в рамках этого тикета — это отдельный тикет 13, и его нельзя начинать, пока `obuhov` не подтверждён владельцем как рабочий.
- Не хардкодить пароль `obuhov` в SQL-миграции или в коде — миграции коммитятся в git.

## Answer

Реализовано в `feature/12-admin-account-obuhov`, смёржено в `develop`.

- `api/src/main/resources/db/migration/V38__admin_obuhov.sql`: `INSERT ... WHERE NOT EXISTS` создаёт `obuhov` с `role='ADMIN'`, `status='ACTIVE'`, `account_type='REGULAR'`, `onboarding_completed_at=now()`. Пароль в миграции — случайный bcrypt-хэш без известного открытого текста (не placeholder-строка, реальный BCrypt-хэш), не пригоден для входа сам по себе.
- `DataInitializer` дополнен строкой `userRepository.findByUsername("obuhov").ifPresent(lifeSphereSeeder::seed)` — засев 9 Сфер жизни тем же общим `LifeSphereSeeder`, идемпотентно.
- Реальный вход выдан через существующий `POST /api/v1/admin/users/{id}/reset-password`, вызванный от `admin` сразу после раскатки миграции — временный пароль показан один раз в ответе API, нигде не закоммичен.
- Проверено в изолированном docker-compose стеке (`.worktrees/12-admin-account-obuhov`, порты 5439/8087/8081/8091) перед деплоем на прод-подобное окружение: создание один раз, отсутствие дублей после `docker compose restart api`.
- Проверено на реально задеплоенной системе (`docker compose build api` + `up -d --force-recreate --no-deps api`):
  - `obuhov` создан (id=12), `role=ADMIN`, `status=ACTIVE`.
  - `reset-password` от `admin` выдал временный пароль, логин `obuhov`/temp-пароль вернул JWT (HTTP 200).
  - `GET /api/v1/auth/me` под `obuhov`: `role=ADMIN`, `onboardingCompleted=true`.
  - `GET /api/v1/life-spheres` под `obuhov`: 9 записей.
  - `POST /api/v1/admin/invite-codes` под `obuhov` успешно выпустил код (`createdByUsername: "obuhov"`).
  - В системе сейчас два активных ADMIN: `admin` и `obuhov` — условие для перехода к тикету 13 выполнено.

