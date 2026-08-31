# Баг Б-4 — кнопки «Привязать Telegram» / «Привязать Max» дают HTTP 403

Type: bug
Status: resolved
Найдено при: релиз 1.0 (живой сайт, Настройки → Импорт из Telegram/Max)
Регрессия в: `release-0.7-ai-import`, тикеты 03 (Telegram) / 04 (Max), Status: resolved

## Доказательство расхождения

Фронт в `SettingsView.vue` шлёт на кнопку «Привязать»:

```js
// linkTelegram  (строки 245-270)
const res = await fetch(`${apiBase()}/bot/telegram/link`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
// linkMax       (строки 321-346)
const res = await fetch(`${apiBase()}/bot/max/link`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
```

Но `TelegramLinkController` и `MaxLinkController` выкладывают только:

```java
// TelegramLinkController.java
@RequestMapping("/api/v1/bot/telegram/link")
@GetMapping                                   // GET  /link        → status
@PostMapping("/disconnect")                   // POST /link/disconnect

// MaxLinkController.java (зеркально)
@RequestMapping("/api/v1/bot/max/link")
@GetMapping
@PostMapping("/disconnect")
```

**Маппинга `POST /api/v1/bot/{telegram,max}/link` (выдача одноразового токена) нет.**
Сервисный метод `issueToken(String username)` существует
(`TelegramLinkService.java:59`, `MaxLinkService.java:62`), но контроллер его не отдаёт.
Запрос `POST /link` падает на `SecurityConfig.anyRequest().authenticated()` — для
неизвестного пути Spring Security отвечает **403** (а не 404), что и видит пользователь
(`api/src/main/java/ru/wolf/api/auth/AuthController.java:57` комментарий подтверждает:
«Spring Security often returns 403 … for unauthenticated API calls»).

`loadTelegramStatus`/`loadMaxStatus` работают (это `GET /link` — он есть), поэтому
статус привязки показывается, а сама привязка (POST) — 403.

## Почему это важно

- Импорт из Telegram/Max (релиз 0.7) недостижим: пользователь не может сгенерировать
  токен привязки, значит бот никогда не привяжется, импорт записей не работает.
- Вертикальный срез не закрыт: сервис готов, контроллер не выложен.

## Что сделать

1. В `TelegramLinkController` добавить `POST /link` (без `/disconnect`), вызывающий
   `linkService.issueToken(user.getUsername())` и возвращающий `TelegramLinkStatus`
   (объект уже содержит `pendingToken`, `linkUrl`, `botUsername`).
2. Зеркально в `MaxLinkController` — `POST /link` → `MaxLinkStatus`.
3. Фронт `linkTelegram`/`linkMax` уже шлёт `POST /link` и ждёт тело статуса — правок
   на фронте не требуется (достаточно, чтобы бэкенд стал отвечать 200 со статусом).
4. `TelegramApiIT`/`MaxLinkApiIT` (если есть): добавить проверку `POST /link` → 200
   и непустой `pendingToken`.
5. Браузерная приёмка: клик «Привязать Telegram» → токен/ссылка появляются, 403 нет.
6. Решить, оставлять ли `resolved` у 0.7-03/0.7-04 — контроллерный маппинг не выложен;
   статус меняет владелец.

## Как закрыт

Закрыт в тикете `issues/04-bot-link-post-endpoint.md` (добавлен `POST /link` в оба
контроллера, возвращающий статус со свежим токеном).
