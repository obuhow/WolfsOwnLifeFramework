# Тикет 07 — Демо-режим для гостя + админ-пользователи

Status: ready-for-agent
Blocked by:
Type: task

## Вопрос

Для неавторизованного пользователя главный экран — незаполненная форма авторизации.
Внизу формы кнопка «Демо-режим»: при клике открывается список предзаполненных профилей
«Рабочий класс», «Мудрый фрилансер», «Свободный художник». При клике на профиль
создаётся демо-пользователь с логином вида `GoodEnoughDemoUser-A1`…`GoodEnoughDemoUser-Z1`,
`GoodEnoughDemoUser-A2`…, пароль = логин. Демо-пользователи удаляются админом вручную
(вкладка «Администрирование», см. тикет 06). (Доработка 3 заявки.)

## Готовая инфраструктура (не изобретать заново)

- `User.accountType` уже есть: `REGULAR | DEMO` (`user/User.java:103-106`);
  `User.expiresAt` — только для DEMO (`User.java:111-113`).
- `UserRepository.findExpiredDemoAccounts(now)` (`UserRepository.java:45-46`) — для
  будущей очистки; в заявке удаление **только ручное**, но поле готово.
- Демо-профили уже лежат в `assets/profiles/{worker-class,wise-freelancer,free-artist}.json`
  с `displayName` «Рабочий класс» / «Мудрый фрилансер» / «Свободный художник»
  (см. `DemoProfile.java`, `DemoFixtureGenerator.populate`).
- `AdminController` уже отдаёт `GET /admin/users?includeDemo` и
  `POST /admin/users/{id}/delete` с `DeleteUserRequest.confirmUsername`
  (защита от удаления последнего ADMIN уже в `AdminService.deleteUser`).

## Что сделать

### 7.1 Бэкенд — публичная регистрация демо-пользователя

1. Новый `DemoPublicController` (или метод в `OnboardingController`) с маппингом
   `POST /api/v1/demo/register`, **permitAll** (добавить в `SecurityConfig` рядом с
   `/api/v1/demo/**`, уже разрешён строкой 55, но убедиться, что `/demo/register`
   попадает под `/api/v1/demo/**`).
2. Тело запроса: `{ "profileSlug": "worker-class" | "wise-freelancer" | "free-artist" }`.
3. Генерация логина: сканировать существующие `User` с `accountType='DEMO'` и
   `username` LIKE `GoodEnoughDemoUser-%`; найти первый свободный слот в порядке
   `A1,B1,…,Z1,A2,B2,…,Z2,…` (буква A–Z, затем инкремент числа).
4. Создать `User`: `username`, `password_hash = bcrypt(username)` (пароль = логин),
   `accountType='DEMO'`, `role='USER'`, `status='ACTIVE'`, `timezone` из профиля.
5. `DemoFixtureGenerator.populate(user, profile)` — загрузить предзаполненные данные.
6. Вернуть `{ username, password, token }` — выпустить JWT, чтобы SPA сразу залогинила
   гостя (плавный вход в демо), и показать логин/пароль (на случай повторного входа).

### 7.2 Фронт — кнопка «Демо-режим» на экране входа

1. `LoginView.vue` — под формой добавить кнопку «Демо-режим»
   (`type="button"`, не сабмит). По клику — модалка/список из трёх профилей
   (названия брать из `GET /api/v1/demo/profiles` или хардкодить три известных slug'а).
2. При выборе профиля — `POST /api/v1/demo/register` → сохранить `token` в
   `localStorage`, перейти на `/today` (онбординг для демо считается пройденным —
   проставить `onboardingCompletedAt` при создании, чтобы не запирать гостя в мастере).
3. Показать плашку «Демо-пользователь <login>, пароль совпадает с логином».

### 7.3 Админ — фильтр демо (связь с тикетом 06)

- `GET /admin/users?includeDemo=true` уже возвращает DEMO-аккаунты (`UserAdminResponse`
  несёт `accountType`). В `AdminUsersView` (тикет 06) показать колонку «Тип» и
  действие «Удалить» для DEMO.

## Testing Decisions

- `DemoRegisterApiIT`: `POST /api/v1/demo/register` без тока (аноним) → 200, тело
  содержит `username` вида `GoodEnoughDemoUser-A1`, `password == username`, непустой
  `token`; созданный `User` имеет `accountType='DEMO'` и загруженные проекты/Дела.
- Повторный вызов → следующий слот (`…-B1`, …, после Z1 — `…-A2`).
- `GET /admin/users?includeDemo=true` под ADMIN видит созданного демо-пользователя;
  `POST /admin/users/{id}/delete` с `confirmUsername` удаляет его.
- Браузерная приёмка: неавторизованный гость → форма входа → «Демо-режим» →
  выбор «Рабочий класс» → попадает в `/today` с предзаполненными данными; выход и
  вход по `GoodEnoughDemoUser-A1` / пароль работает.

## Out of Scope

- Авто-удаление демо по `expiresAt` по расписанию — только ручное (по требованию
  Павла); поле `expiresAt` можно не заполнять.
- Регистрация обычных пользователей по коду — уже есть (`/auth/register`), не затрагивается.

## Решение владельца (security)

Публичный `POST /api/v1/demo/register` создаёт пользователя с паролем = логин. Митигация:
логины не угадываемы (`GoodEnoughDemoUser-<буква><N>`), аккаунты изолированы по tenant,
удаляются админом. Брутфорс ограничен префиксом. Зафиксировано в `grill.md`.
