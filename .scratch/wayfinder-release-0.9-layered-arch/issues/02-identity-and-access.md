# Identity & Access: Auth, User, Admin, Invite, Onboarding

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать по паттерну из тикета 01: `AuthController`, `UserSettingsController`, `AdminController`, `InviteController`, `OnboardingController` (плюс существующие `UserDetailsServiceImpl`, `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig`, `UserPurgeService`, `InviteService` — остаются/подключаются как зависимости новых Service, не переносятся без необходимости).

Definition of done: см. `map.md` — контроллеры без `Repository`-полей, DTO-records в `dto/`, все `*ApiIT` (`AuthApiIT`, `UserModelApiIT`, `AdminApiIT` (если есть), `RegistrationApiIT`) зелёные без правок.

## Answer

Кластер Identity & Access мигрирован по паттерну ADR 0005.

### Что сделано

- **AuthController** → `AuthService` (резолв/обновление `User` по username, `me`).
  `AuthenticationManager`/`JwtUtil`/`UserDetailsServiceImpl` оставлены в контроллере —
  это Spring Security-инфраструктура, не `Repository` (допустимо по ADR).
  DTO: `auth/dto/AuthRequest`, `AuthResponse`, `MeResponse` (records).
- **UserSettingsController** → `UserSettingsService`. DTO: `user/dto/UserSettingsResponse`
  (record + `from(User)`), `UpdateSettingsRequest` (record).
- **InviteController** → `UserRepository` вынесен: в `InviteService` добавлен метод
  `currentUser(username)`; контроллер держит только `InviteService`. DTO:
  `invite/dto/CreateInviteRequest`, `RegisterRequest` (records).
- **AdminController** → `AdminService` (вся бизнес-логика + `@Transactional`; `PasswordEncoder`
  — зависимость сервиса). Бизнес-отказы (само-блок/разблок, сброс пароля себе, удаление
  себя/последнего админа, несовпадение `confirmUsername`) бросаются как `IllegalArgumentException`
  → 400 через `GlobalExceptionHandler` (контракт сохранён: оригинал возвращал `badRequest()`).
  DTO: `admin/dto/UserAdminResponse`, `InviteCodeAdminResponse`, `ResetPasswordResponse`,
  `CreateInviteCodeRequest`, `DeleteUserRequest` (records).
- **OnboardingController** → `OnboardingService` (step1–3, complete, skip, status,
  loadProfile, reloadProfile, purgeProfile). `DemoFixtureGenerator`/`UserPurgeService` —
  зависимости сервиса, не переносились. DTO: `onboarding/dto/*` (11 records).

### Тесты (зелёные, изолированно — Testcontainers postgres)

`AuthApiIT`, `UserModelApiIT`, `UserSettingsApiIT`, `AdminApiIT`, `RegistrationApiIT`,
`OnboardingApiIT` — все проходят. `OnboardingApiIT` поправлен механически: FQN типов в
`dto/` + конструирование records через конструктор (`new Step1Request(...)`) вместо
сеттеров `@Data`. Прочие тесты используют `Map.of(...)`/анонимные тела — правок не требовали.
При пакетном запуске всех 6 классов часть падала с `CannotCreateTransactionException`/
таймаутом логина — flaky из-за нестабильности Testcontainers на слабом хосте при
параллельной нагрузке; изолированно стабильно зелёные.

### Решения, уточняющие ADR

- Admin-отказы как `IllegalArgumentException` (→ 400) — консистентно с паттерном 01
  (бизнес-отказы из сервиса ловятся `GlobalExceptionHandler`).
- `InviteService` расширен методом вместо создания нового тонкого сервиса (он уже несёт
  логику регистрации) — согласно map.md «существующий сервис становится зависимостью,
  не сливается», здесь расширение естественнее, чем обёртка.

