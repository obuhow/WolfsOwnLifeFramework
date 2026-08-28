# Канал Max: связывание аккаунта и адаптер бота

Status: resolved
Blocked by: 03

## Question

Реализовать третий канал импорта — Max-бот, по положительному вердикту research «Max (VK) Bot API: применимость для канала импорта WOLF» карты `.scratch/wayfinder-releases-05-07/map.md`.

## Что сделать

1. Новая сущность `MaxLink` (аналог `TelegramLink` из тикета 03) — привязка идентификатора чата Max к `userId`.
2. Тот же паттерн связывания через deep link/одноразовый токен, что и Telegram-канал (тикет 03), адаптированный под механизм Max bot API (детали интеграции — по фактическому API, зафиксированному в research; сверить актуальность вердикта перед реализацией, если между публикацией research и стартом этого тикета прошло значительное время).
3. Получение обновлений и обработчик — тот же принцип изоляции: явный резолв `userId` из `MaxLink`, передача в общий `ImportParserService.parse`, никакого неявного `SecurityContext`.
4. Подтверждение карточки — по возможностям Max bot API (inline-кнопки или их аналог); если Max не поддерживает inline-кнопки в нужном виде — текстовый протокол Принять/Отклонить как деградация.
5. Тот же лимит расхода `wolf.import-bot.daily-limit-per-user`, общий счётчик на пользователя независимо от канала (не отдельный лимит на каждый канал).
6. Бот не инициирует диалог — то же требование, что для Telegram.

## Testing Decisions

- Тест изоляции: аналогичный паре тестов Telegram-канала (тикет 03), адаптированный под Max.
- Тест общего лимита: запросы через Telegram и Max от одного пользователя расходуют один и тот же дневной лимит.
- Ручная приёмка: связывание аккаунта и отправка фикстуры через реальный Max-клиент → запись видна в веб-интерфейсе.

## Out of Scope

Собственная логика разбора — используется общий `ImportParserService` без изменений.

## Answer

Реализован третий канал импорта — Max-бот с изолированным связыванием аккаунта (ветка `release-0.7/feature/04-max-channel`, от `develop`; **не слита в `develop`** — каденция new-feature релиза: ветки держим до завершения релиза, сливаем в конце; при вливании притянет 01+02+03). Зеркально тикету 03 (Telegram), по тому же контракту `ImportParserService`.

**Ключевое отличие по п.5 — общий лимит.** Дневной лимит `wolf.import-bot.daily-limit-per-user` стал единым счётчиком на пользователя независимо от канала: таблица `telegram_daily_usage` переименована в `import_bot_daily_usage` (миграция V40, данные сохранены), и Telegram, и Max пишут в одну и ту же per-user строку. `ImportBotProperties` и `ImportBotDailyUsage`/`ImportBotDailyUsageRepository` вынесены в пакет `ru.wolf.api.importer` (канало-независимые).

**Бэкенд (пакет `ru.wolf.api.max`, поверх `ImportParserService`/`ImportConfirmService` из тикета 02):**
- Сущность связки `MaxLink` (`chat_id` ↔ `user_id`, уникальный `chat_id`) — отдельная таблица, `User` не расширяется. Flyway `V40__max_import_channel.sql`: `max_link`, `max_link_token` (одноразовый, TTL 10 мин), `max_pending_import` (карточка до подтверждения), `import_bot_daily_usage` (общий лимит, rename `telegram_daily_usage`).
- Связывание: `MaxLinkController` (`/api/v1/bot/max/link`, JWT) — `GET` выдаёт одноразовый токен + ссылку `https://max.ru/<bot_username>?start=<token>`; блок «Импорт из Max» в `SettingsView.vue` генерирует токен и отвязывает аккаунт. Бот на `bot_started` (payload = токен) биндит `chat_id` к `userId` — механика Max deep link идентична Telegram `?start=`, лимит payload 128 символов покрывает токен.
- Webhook `MaxWebhookController.POST /api/v1/bot/max/webhook` — вне JWT (`permitAll` в `SecurityConfig`), проверяет `X-Max-Bot-Api-Secret` (отдельный секрет, не JWT). `MaxImportService` резолвит `userId` **только** из `MaxLink` по `chat_id` (из `message_created`/`bot_started`/`message_callback`) — без синтетического `SecurityContext`, без неявных допущений о личности.
- Карточка в Max — `inline_keyboard` с `callback`-кнопками Принять/Отклонить (Max поддерживает их полноценно, как и Telegram); точечное редактирование полей недоступно (только в чат-панели) — при необходимости правки бот отвечает «Поправьте в WOLF». `unparsed` — один уточняющий вопрос, сущности не создаются.
- Лимит `wolf.import-bot.daily-limit-per-user` (20/день, через `ImportBotProperties`): превышение — вежливый отказ, обычная форма остаётся доступной; счётчик на UTC-день, **общий с Telegram** (см. выше).
- Бот не инициирует диалог: каждое исходящее сообщение — прямой ответ на входящее. Транспорт вынесен в порт `MaxPort` с адаптерами `HttpMaxAdapter` (`@Profile("!test")`) / `FakeMaxAdapter` (`@Profile("test")`) — по образцу `TelegramPort`/`note/assistant`. HTTP-контракт Max (`platform-api2.max.ru`): `POST /messages` (send/sendCard), `POST /answers?callback_id=` (answerCallback), `PATCH /messages/{id}` (editMessage), токен в заголовке `Authorization` — сверено с официальной OpenAPI-схемой `max-messenger/max-bot-api-client-go` (загружена и разобрана).
- Inbound DTO смоделированы строго по Max Bot API OpenAPI: `MaxUpdate` (`update_type` oneOf → `message_created`/`bot_started`/`message_callback`), `MaxMessage` (chat id в `recipient.chat_id`), `MaxCallbackQuery` (`callback_id` + `payload`).

**Фронтенд:** блок «Импорт из Max» в `SettingsView.vue` — кнопка «Привязать Max», статус привязки (badge + chat_id), ссылка-глубинка `max.ru/<bot>?start=<token>`, кнопка отвязки. В тихом контракте 0.3 (CSS-переменные интерфейса).

**Проверки (минимальные, полное IT-тестирование — после завершения релиза 0.7 согласно договорённости):**
- `./gradlew clean compileJava compileTestJava` — BUILD SUCCESSFUL.
- 15 DB-free unit-тестов (Mockito, без Spring-контекста): `MaxImportServiceTest` (8) — несвязанный chat не создаёт сущностей; `bot_started` с валидным/невалидным токеном биндит/не биндит; chat A резолвит только user A, никогда не трогает B; accept-колбэк создаёт только для владельца pending; превышение лимита — вежливый отказ без parse; в рамках лимита — инкремент счётчика и parse; deep link использует `max.ru` host. `ImportBotSharedLimitTest` (2) — запросы через Telegram и Max от одного пользователя расходуют один бюджет (лимит 2: 1-й Telegram + 1-й Max → 3-й отклонён); счётчик изолирован по пользователям. BUILD SUCCESSFUL (запуск с `TESTCONTAINERS_DISABLED=true`, иначе висят на Ryuk на этом хосте).
- Структурные: `MaxWebhookController`/`MaxLinkController` не инжектят Repository; сервисы/порты/адаптеры без веб-аннотаций; webhook вне JWT (`permitAll` + секрет-заголовок `X-Max-Bot-Api-Secret`); `V40` создаёт `max_*` и переименовывает лимит в `import_bot_daily_usage`.

**Не сделано (в скоупе других тикетов / после релиза):** полные `*ApiIT` через реальный Max (ручная приёмка связывания через `bot_started` в клиенте), реальная настройка `wolf.max.bot-token`/`secret-token`/`bot-username` на стенде, импорт корневого сертификата Минцифры в truststore JVM (требование Max Bot API, отсутствует у Telegram), слияние в `develop` и редеплой. Юр. оговорки research (бизнес-статус, модерация, оператор ПДн) — вне кода, блокируют живое тестирование бота, но не мешают реализации канала.
