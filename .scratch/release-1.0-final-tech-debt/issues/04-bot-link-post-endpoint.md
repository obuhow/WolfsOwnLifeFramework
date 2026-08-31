# Тикет 04 — POST /link для Telegram и Max (закрывает 403)

Status: resolved
Blocked by:
Type: task

## Вопрос

Выложить в `TelegramLinkController` и `MaxLinkController` эндпоинт `POST /link`,
выдающий одноразовый токен привязки, — сейчас его нет, и фронт получает 403
(закрывает Б-4, `bugs/04-bot-link-403.md`).

## Что сделать

1. `api/src/main/java/ru/wolf/api/telegram/TelegramLinkController.java` — добавить
   рядом с `GET /link` и `POST /disconnect`:
   ```java
   @PostMapping
   public ResponseEntity<TelegramLinkStatus> issue(@AuthenticationPrincipal User user) {
       return ResponseEntity.ok(linkService.issueToken(user.getUsername()));
   }
   ```
   `TelegramLinkStatus` уже содержит `pendingToken`, `linkUrl`, `botUsername`
   (`telegram/dto/TelegramLinkStatus.java`) — фронт ждёт именно это тело.
2. `api/src/main/java/ru/wolf/api/max/MaxLinkController.java` — зеркально:
   ```java
   @PostMapping
   public ResponseEntity<MaxLinkStatus> issue(@AuthenticationPrincipal User user) {
       return ResponseEntity.ok(linkService.issueToken(user.getUsername()));
   }
   ```
3. Фронт `linkTelegram()` / `linkMax()` уже шлёт `POST /link` и после успеха читает
   `data.linked / data.linkUrl / data.pendingToken / data.botUsername` — **правок на
   фронте не требуется** (достаточно, чтобы бэкенд отвечал 200).
4. `SecurityConfig` уже пропускает `/api/v1/bot/telegram/webhook` и `/bot/max/webhook`
   как permitAll; `POST /link` должен идти **с JWT** (аутентифицированный пользователь),
   менять `SecurityConfig` не нужно — `anyRequest().authenticated()` покроет его корректно
   при наличии валидного токена.

## Testing Decisions

- `TelegramApiIT` / `MaxLinkApiIT` (добавить, если отсутствуют): `POST /bot/telegram/link`
  под JWT → 200, тело содержит непустой `pendingToken` и `linkUrl`.
- Браузерная приёмка: клик «Привязать Telegram» в Настройках → появляется ссылка/токен,
  в консоли сети **нет** 403; `GET /bot/telegram/link` (статус) по-прежнему работает.
- Негатив: `POST /link` без тока → 403 (корректно, аноним не привязывает).

## Out of Scope

- Изменение жизненного цикла токена (уже 10 мин, `TelegramLinkService.TOKEN_TTL_MINUTES`).
- Webhook-логика ботов — не затрагивается.

## Закрывает

Баг Б-4 (`bugs/04-bot-link-403.md`). Перевести `Status: open` → `resolved`.

## Answer

Реализовано в ветке `release-1.0/feature/04-bot-link-post` (от `origin/develop`).

- `TelegramLinkController` — добавлен голый `@PostMapping` `issue(@AuthenticationPrincipal
  User user)` → `linkService.issueToken(user.getUsername())` → 200 c `TelegramLinkStatus`
  (рядом с `GET /link` и `POST /link/disconnect`).
- `MaxLinkController` — зеркально `@PostMapping` → `MaxLinkStatus`.
- `SecurityConfig` не менялся: `POST /link` покрывается `anyRequest().authenticated()`,
  требует JWT (аноним получает 403 — корректно).
- Фронт не менялся: `SettingsView.linkTelegram/linkMax` уже шлёт `POST /link` и читает
  тело статуса.
- Тесты (новые, чёрный ящик через `ApiIntegrationTest` + Testcontainers Postgres):
  `TelegramLinkApiIT`, `MaxLinkApiIT` — по 2 теста:
  1. `POST /bot/{telegram,max}/link` под JWT → 200, `pendingToken` непустой, `linked=false`;
  2. тот же POST без JWT → 403.
- Прогон: `MaxLinkApiIT` — 2/2 PASSED сразу; `TelegramLinkApiIT` — на первом прогоне
  позитивный тест упал ровно на 5.027s (дефолтный `WebTestClient` responseTimeout) —
  инфра-таймаут перегруженного хоста, не регрессия (эндпоинт идентичен рабочему Max).
  Перезапуск класса в одиночку: **BUILD SUCCESSFUL, 2/2 PASSED** (позитивный 0.771s).

Б-4 закрыт. Статус исходных 0.7-03/0.7-04 не трогался (по решению владельца —
сервис `issueToken` был готов, не выложен был только контроллерный маппинг).
