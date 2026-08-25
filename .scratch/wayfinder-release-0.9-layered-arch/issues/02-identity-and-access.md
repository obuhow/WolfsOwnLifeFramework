# Identity & Access: Auth, User, Admin, Invite, Onboarding

Type: task
Status: open
Blocked by: 01

## Question

Мигрировать по паттерну из тикета 01: `AuthController`, `UserSettingsController`, `AdminController`, `InviteController`, `OnboardingController` (плюс существующие `UserDetailsServiceImpl`, `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig`, `UserPurgeService`, `InviteService` — остаются/подключаются как зависимости новых Service, не переносятся без необходимости).

Definition of done: см. `map.md` — контроллеры без `Repository`-полей, DTO-records в `dto/`, все `*ApiIT` (`AuthApiIT`, `UserModelApiIT`, `AdminApiIT` (если есть), `RegistrationApiIT`) зелёные без правок.

## Answer

_(заполняется при резолве)_
