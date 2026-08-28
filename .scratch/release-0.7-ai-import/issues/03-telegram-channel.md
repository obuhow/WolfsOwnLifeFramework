# Канал Telegram: связывание аккаунта и webhook

Status: resolved
Blocked by: 02

## Question

Реализовать второй канал импорта — Telegram-бот с изолированным связыванием аккаунта, по решениям тикета «Архитектура бота импорта: разборщик, каналы, связывание аккаунтов» карты `.scratch/wayfinder-releases-05-07/map.md`.

## Что сделать

1. Новая сущность `TelegramLink` (`chat_id` ↔ `user_id`, уникальный `chat_id`). Таблица связи, не расширение `User`.
2. Связывание: блок «Импорт из Telegram» в `SettingsView.vue` с кнопкой «Привязать Telegram» — генерирует одноразовый токен (живёт 10 минут), формирует ссылку `https://t.me/<bot_username>?start=<token>`. Обработка `/start <token>` в боте связывает `chat_id` пользователя Telegram с `userId` WOLF.
3. Получение обновлений — webhook, эндпоинт `POST /api/v1/bot/telegram/webhook`, проверка `X-Telegram-Bot-Api-Secret-Token` (не общая JWT-аутентификация).
4. Обработчик webhook явно резолвит `userId` из `TelegramLink` по `chat_id` из тела сообщения и передаёт его в `ImportParserService.parse(user, text)` — без синтетического `SecurityContext`, без неявных допущений о личности отправителя.
5. Подтверждение карточки в Telegram — inline-кнопки Принять/Отклонить; точечное редактирование полей недоступно в Telegram (только в чат-панели) — при необходимости правки бот отвечает «Поправь в WOLF» со ссылкой на веб-интерфейс.
6. Лимит расхода: новый параметр конфигурации `wolf.import-bot.daily-limit-per-user` (число запросов в день на пользователя). Превышение — вежливый текстовый отказ, обычная форма остаётся доступной.
7. Бот не инициирует диалог — ни одного исходящего сообщения, не являющегося прямым ответом на входящее.

## Testing Decisions

- Тест изоляции: webhook-запрос с `chat_id`, не связанным ни с одним `userId` — отклонён, ничего не создано.
- Тест изоляции: `chat_id`, связанный с пользователем A, не может повлиять на данные пользователя B ни при каких значениях полей запроса.
- Тест лимита: превышение `daily-limit-per-user` — вежливый отказ, счётчик сбрасывается на следующий день.
- Ручная приёмка: `/start <token>` в реальном Telegram-клиенте → сообщение с текстом одной из фикстур тикета 01 → запись видна в веб-интерфейсе того же аккаунта.

## Out of Scope

Max-канал (тикет 04) — отдельная реализация по аналогичному контракту. Редактирование полей карточки прямо в Telegram — не реализуется, только Принять/Отклонить.

## Answer
Реализован второй канал импорта — Telegram-бот с изолированным связыванием аккаунта (ветка `release-0.7/feature/03-telegram-channel`, от тикета 02; **не слита в `develop`** — каденция new-feature релиза: ветки держим до завершения релиза; при вливании притянет 01+02).

**Бэкенд (поверх `ImportParserService`/`ImportConfirmService` из тикета 02):**
- Сущность связки `TelegramLink` (`chat_id` ↔ `user_id`, уникальный `chat_id`) — отдельная таблица, `User` не расширяется. Flyway `V39__telegram_import_channel.sql`: `telegram_link`, `telegram_link_token` (одноразовый, TTL 10 мин), `telegram_pending_import` (карточка до подтверждения), `telegram_daily_usage` (лимит).
- Связывание: `TelegramLinkController` (`/api/v1/bot/telegram/link`, JWT) — `GET` выдаёт одноразовый токен + ссылку `https://t.me/<bot_username>?start=<token>`; блок «Импорт из Telegram» в `SettingsView.vue` генерирует токен и отвязывает аккаунт. Бот на `/start <token>` биндит `chat_id` к `userId`.
- Webhook `TelegramWebhookController.POST /api/v1/bot/telegram/webhook` — вне JWT (`permitAll` в `SecurityConfig`), проверяет `X-Telegram-Bot-Api-Secret-Token` (отдельный секрет, не JWT). `TelegramImportService` резолвит `userId` **только** из `TelegramLink` по `chat_id` из тела — без синтетического `SecurityContext`, без неявных допущений о личности.
- Карточка в Telegram — inline-кнопки Принять/Отклонить; точечное редактирование полей недоступно (только в чат-панели) — при необходимости правки бот отвечает «Поправьте в WOLF». `unparsed` — один уточняющий вопрос, сущности не создаются.
- Лимит `wolf.import-bot.daily-limit-per-user` (20/день, через `ImportBotProperties`): превышение — вежливый отказ, обычная форма остаётся доступной; счётчик на UTC-день.
- Бот не инициирует диалог: каждое исходящее сообщение — прямой ответ на входящее. Транспорт вынесен в порт `TelegramPort` с адаптерами `HttpTelegramAdapter` (`@Profile("!test")`) / `FakeTelegramAdapter` (`@Profile("test")`) — по образцу `note/assistant`.

**Фронтенд:** блок «Импорт из Telegram» в `SettingsView.vue` — кнопка «Привязать Telegram», статус привязки (badge + chat_id), ссылка-глубинная для бота, кнопка отвязки. В тихом контракте 0.3 (CSS-переменные интерфейса).

**Проверки (минимальные, полное IT-тестирование — после завершения релиза 0.7 согласно договорённости):**
- `./gradlew clean compileJava compileTestJava` — BUILD SUCCESSFUL.
- 5 DB-free unit-тестов `TelegramImportServiceTest` (Mockito, без Spring-контекста): несвязанный chat не создаёт сущностей; chat A резолвит только user A, никогда не трогает B; accept-колбэк создаёт только для владельца pending; превышение лимита — вежливый отказ без parse; в рамках лимита — инкремент счётчика и parse. BUILD SUCCESSFUL.
- Структурные: `TelegramWebhookController`/`TelegramLinkController` не инжектят Repository; сервисы/порты/адаптеры без веб-аннотаций; webhook вне JWT (`permitAll` + секрет-заголовок).

**Не сделано (в скоупе других тикетов / после релиза):** полные `*ApiIT` через реальный Telegram (ручная приёмка `/start <token>` в клиенте), реальная настройка `wolf.telegram.bot-token`/`secret-token` на стенде, слияние в `develop` и редеплой.
