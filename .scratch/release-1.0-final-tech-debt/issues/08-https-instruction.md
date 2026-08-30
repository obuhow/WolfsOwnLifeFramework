# Тикет 08 — Инструкция по подключению HTTPS-сертификата

Status: resolved
Blocked by:
Type: task

## Вопрос

Написать для владельца (Павла) пошаговую инструкцию, что сделать, чтобы подключить
HTTPS-сертификат к самохостингу WOLF (Доработка 4 заявки). Это **документ**, не код.

## Что сделать

1. Создать `docs/self-hosting-https.md` (или раздел в `web/src/components/docs/selfHostingView.vue`
   — там уже страница «Самостоятельный хостинг», main.js:124). Инструкция покрывает
   текущий стек: docker-compose + nginx (фронт `web/dist` отдаёт nginx, проксирует
   `/api/v1` на `api`), Postgres.
2. Содержание:
   - Вариант A (рекомендован): **Caddy** вместо/перед nginx — авто-HTTPS через ACME
     (Let's Encrypt), один блок `reverse_proxy`. Показать `Caddyfile`.
   - Вариант B: **certbot + nginx** — получение wildcard/одиночного сертификата,
     `fullchain.pem`/`privkey.pem`, правка `nginx.conf` (`listen 443 ssl;`,
     `ssl_certificate`, `ssl_certificate_key`, `return 301 https://$host$request_uri;`
     на 80), перенаправление `/api/v1` и HSTS.
   - Вариант C (локально/без домена): self-signed + доверие в OS — только для теста.
   - Обновление сертификата (cron / systemd timer для certbot; Caddy — авто).
   - Проверка: `curl -I https://<domain>`, отсутствие смешанного контента
     (SPA грузит `/api/v1` по тем же origin — относительный путь, проблем нет).
3. Упомянуть, что `VITE_API_BASE` по умолчанию `/api/v1` (относительный) — при HTTPS
   смешанного контента не будет, менять не надо; если используется абсолютный
   `VITE_API_BASE=http://...`, заменить на `https://`.
4. Ссылку на инструкцию добавить в README и в самораздел «Самостоятельный хостинг».

## Testing Decisions

- Документ проходит проверку «воспроизводимости» владельцем (Павел применяет на своём
  хосте). Кодовых тестов нет.
- Ссылка из `SelfHostingView.vue` и README ведёт на файл/раздел.

## Out of Scope

- Автоматическая выдача сертификата из кода WOLF — только инфраструктурная инструкция.
- Wildcard DNS / выпуск сертификата в CI.

## Закрывает

Доработка 4 заявки.

## Answer

Реализовано в ветке `release-1.0/feature/08-https-instruction` (от `origin/develop`).

- Создан `docs/self-hosting-https.md` — пошаговая инструкция под текущий стек
  (docker-compose: nginx `web` слушает 80 и проксирует `/api/` на `api:8080`,
  отдельный контейнер `docs`). Покрывает:
  - **Вариант A (рекомендован) — Caddy**: сервис `caddy` перед `web`, готовый
    `Caddyfile` с `reverse_proxy web:80`, авто-ACME (Let's Encrypt), тома
    `caddy_data`/`caddy_config`, авто-продление.
  - **Вариант B — certbot + nginx**: получение `fullchain.pem`/`privkey.pem`,
    полный 443-`server` в `nginx.conf` (перенос текущих `location` `/api/`,
    `/docs/`, `/`), редирект 80→443, HSTS, применение через
    `up -d --force-recreate web`, авто-продление deploy-hook'ом `nginx -s reload`.
  - **Вариант C — self-signed**: только для локального теста.
  - Проверка: `curl -I https://<domain>`, отсутствие смешанного контента
    (SPA грузит `/api/v1` относительным путём — mixed content не возникает).
  - Явно указано: `VITE_API_BASE` по умолчанию `/api/v1` (относительный) — при HTTPS
    менять не нужно; абсолютный `http://` → заменить на `https://` и пересобрать.
- Ссылка на инструкцию добавлена в `README.md` (блок «Публичная установка» в
  «Быстром старте» и раздел «Документация»).
- В `web/src/components/docs/SelfHostingView.vue` добавлена секция «HTTPS-сертификат»
  (пункт оглавления `#https` + краткое описание трёх вариантов и ссылка на файл).
- Приёмка: `npm run build` в `web/` — компонент компилируется (визуально проверяется
  на общем этапе приёмки; кодовых тестов у документа нет по Testing Decisions).

Доработка 4 закрыта.
