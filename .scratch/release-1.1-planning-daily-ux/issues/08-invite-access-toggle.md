# Тикет 08 — Чекбокс «Открыть доступ по инвайтам» в настройках

Status: resolved
Blocked by:
Type: task

## Вопрос

На логин-экране (`/login`) всегда видна ссылка «У меня есть код» (регистрация по
инвайту). Нужен глобальный переключатель экземпляра, который администратор ставит в
Настройках:

- **Галочка активна (по умолчанию)** — поведение как сейчас: ссылка «У меня есть код»
  отображается, регистрация по инвайту работает.
- **Галочка снята** — ссылка «У меня есть код» на логин-экране не отображается, и
  регистрация по инвайту на бэкенде закрыта (чтобы скрытие ссылки не было чисто
  косметическим — `/auth/register` без действующего флага возвращает отказ).

Это настройка **всего экземпляра**, а не отдельного пользователя: логин-экран анонимный и
не знает, чьи персональные настройки читать. Поэтому заводим отдельное хранилище
глобальных настроек экземпляра (`instance_config`, singleton-строка id=1), независимое от
персонального `User`-объекта и `/api/v1/settings`.

## Доказательство текущего состояния

- `web/src/components/LoginView.vue:135` — ссылка всегда видна:
  `<p class="login-hint"><a href="#/register">У меня есть код</a></p>`.
- `api/src/main/java/ru/wolf/api/invite/InviteService.java:80` `registerWithInvite(...)`
  не проверяет никакой глобальный флаг — регистрация открыта всегда при валидном коде.
- Персональные настройки (`UserSettingsController` → `User`-сущность) требуют
  `Authentication`, логин-экрану недоступны.

## Что сделать

Бэкенд (`api`, пакет `ru.wolf.api.instance`, ADR 0005 — тонкий MVCS):

1. Миграция `V40__instance_config.sql`: таблица `instance_config` (singleton `id=1`,
   `invite_access_open BOOLEAN NOT NULL DEFAULT TRUE`), seed-строка `id=1, TRUE`.
2. `InstanceConfig` (entity), `InstanceConfigRepository` (JpaRepository).
3. `InstanceConfigService`: `isInviteAccessOpen()` (read-only, default `true` при
   отсутствии строки) и `setInviteAccessOpen(boolean)`.
4. `InstanceController`:
   - `GET /api/v1/instance/registration` — **permitAll**, возвращает
     `{ inviteAccessOpen: boolean }`. Читает логин-экран.
   - `PUT /api/v1/instance/invite-access` — `@PreAuthorize("hasRole('ADMIN')")`,
     принимает `{ inviteAccessOpen: boolean }`, сохраняет, возвращает актуальное
     состояние. Переключатель в Настройках.
5. `SecurityConfig`: `permitAll` для `/api/v1/instance/registration`.
6. `InviteService.registerWithInvite(...)`: в начале, если
   `instanceConfigService.isInviteAccessOpen() == false` — бросить
   `IllegalStateException("Регистрация по инвайтам отключена")`
   (контроллер вернёт 400, RegisterView покажет сообщение).

Фронт (`web`):

7. `SettingsView.vue` — в админ-секции «Администрирование» добавить чекбокс
   «Открыть доступ по инвайтам» + кнопку «Сохранить». Загрузка текущего значения через
   `GET /api/v1/instance/registration` (с токеном), сохранение — `PUT
   /api/v1/instance/invite-access`. Отдельный fetch от персональных настроек.
8. `LoginView.vue` — при `onMounted` сделать `GET /api/v1/instance/registration`
   (без токена) и спрятать ссылку «У меня есть код», если `inviteAccessOpen == false`.
   Значение по умолчанию `true`, чтобы при сбое загрузки поведение оставалось «как сейчас».

## Testing Decisions

- `GRADLE_OPTS=-Xmx768m ./gradlew --no-daemon clean compileJava compileTestJava` — зелёный.
- `cd web && npm run build` — зелёный (vite).
- Headless-проверка собранного бандла: маркеры `inviteAccessOpen`,
  `/api/v1/instance/registration`, «Открыть доступ по инвайтам» присутствуют.
- Live (после передеплоя): `GET /api/v1/instance/registration` → 200,
  `{"inviteAccessOpen":true}`; `PUT` под admin → меняет значение; снятая галочка скрывает
  ссылку на `/login`; `POST /api/v1/auth/register` с валидным кодом при выключенном флаге →
  400 «Регистрация по инвайтам отключена».
- Браузерная приёмка: headless Chromium + CDP — ссылка видна при ON, скрыта при OFF.

## Out of Scope

- Персонализация флага по пользователям — флаг глобальный.
- Изменение формы выпуска инвайт-кодов (AdminInvitesView) — не затрагивается.

## Закрывает

Доработка «чекбокс доступа по инвайтам» релиза 1.1.

## Answer

Ветка `release-1.1/feature/08-invite-access-toggle` (от `develop` @ 714a9c2),
коммиты `5a64807` + `af7f8e3` + `0c028ec`, смержено в `develop`
(`2612087` + `cade3f5` + `64c25d3`), задеплоено, live-проверено.

**Бэкенд** (пакет `ru.wolf.api.instance`, ADR 0005 — тонкий MVCS):
- Миграция **V42** `instance_config.sql` (V40/V41 на develop заняты — max_import_channel,
  hours_per_delo; изначально заведена как V40, переименована при мерже).
  Singleton-строка `id=1`, `invite_access_open BOOLEAN NOT NULL DEFAULT TRUE` + seed.
- `InstanceConfig` (entity), `InstanceConfigRepository`, `InstanceConfigService`
  (`isInviteAccessOpen()` default true при отсутствии строки, `setInviteAccessOpen`).
- `InstanceController`: `GET /api/v1/instance/registration` (permitAll) +
  `PUT /api/v1/instance/invite-access` (`@PreAuthorize("hasRole('ADMIN')")`).
- `SecurityConfig`: permitAll для `/api/v1/instance/registration`.
- `InviteService.registerWithInvite(...)`: при выключенном флаге —
  `IllegalArgumentException("Регистрация по инвайтам отключена")` → 400 с этим
  сообщением (блокировка на бэкенде, а не только скрытие ссылки). Изначально бросался
  `IllegalStateException`, но `InviteController` заворачивает его в общее
  «Код недействителен» — переведено на `IllegalArgumentException`, который мапится в
  `e.getMessage()`.

**Фронт**:
- `SettingsView.vue` — админ-секция «Администрирование»: чекбокс «Открыть доступ по
  инвайтам» + кнопка «Сохранить» (отдельный fetch от персональных настроек).
- `LoginView.vue` — `onMounted` читает публичный `/api/v1/instance/registration`,
  ссылка «У меня есть код» скрыта при `inviteAccessOpen == false`; при сбое загрузки —
  по умолчанию видна (прежнее поведение).

**Проверено:**
- `GRADLE_OPTS=-Xmx768m ./gradlew --no-daemon clean compileJava compileTestJava` — зелёный.
- `npm run build` (vite, 119 модулей) — зелёный; маркеры `inviteAccessOpen`,
  `instance/registration`, «Открыть доступ по инвайтам» присутствуют в бандле.
- DB-free unit: `InstanceConfigServiceUnitTest` (3) + `InviteServiceInviteAccessUnitTest`
  (1) — зелёные (TESTCONTAINERS_DISABLED).
- Live: `GET /api/v1/instance/registration` → 200 `{"inviteAccessOpen":true}`;
  `PUT /api/v1/instance/invite-access` под admin → меняет значение; при выключенном
  флаге `POST /api/v1/auth/register` → 400 `{"message":"Регистрация по инвайтам
  отключена"}`; флаг восстановлен в `true`; V42 применена (таблица
  `instance_config` существует, строка `1 | t`). Бандл в `wolf-web` содержит маркеры
  `inviteAccessOpen`/`instance/registration`/«Открыть доступ по инвайтам».
- **Браузерная приёмка (headless Chromium + CDP, скрипт по образцу
  `scripts/cdp_driver.py`):** при флаге ON ссылка «У меня есть код» видна
  (`visible:true`); PUT false под admin → 200; при флаге OFF ссылки в DOM нет
  (`querySelector('a[href="#/register"]')` → `null`); флаг восстановлен в true.
  `BROWSER_GATE_PASSED`.
