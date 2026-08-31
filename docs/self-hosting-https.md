# Подключение HTTPS к самохостингу WOLF

> Инструкция для владельца, разворачивающего WOLF у себя через `docker-compose.yml`.
> Это инфраструктурная настройка вокруг стека WOLF — сам код приложения ничего не
> меняет и сертификаты не выпускает.

## Что уже есть в стеке

Стек WOLF (`docker-compose.yml`) поднимает четыре контейнера:

- `db` — Postgres 16 (наружу не публикуется);
- `api` — Spring Boot на `8080` (наружу не публикуется, доступ через nginx контейнера `web`);
- `web` — nginx, слушает `80`, отдаёт собранный SPA (`web/dist`) и проксирует `/api/`
  на `api:8080`, а `/docs/` — на контейнер `docs`;
- `docs` — nginx со статикой документации.

Наружу открыт только порт `web` (`${WEB_HTTP_PORT:-80}:80`). Именно перед ним нужно
поставить TLS. SPA обращается к API по относительному пути `/api/v1` (тот же origin),
поэтому после включения HTTPS **смешанного контента не будет** и менять `VITE_API_BASE`
не требуется.

Ниже — три варианта. **Вариант A (Caddy) рекомендуется**: он сам получает и продлевает
сертификат Let's Encrypt.

---

## Вариант A (рекомендован) — Caddy с авто-HTTPS

Caddy ставится перед контейнером `web` и терминирует TLS, автоматически запрашивая
сертификат по ACME (Let's Encrypt) и продлевая его. Требуется:

- домен (например `wolf.example.com`), A/AAAA-запись которого указывает на сервер;
- открытые снаружи порты `80` и `443` (80 нужен для ACME HTTP-01 challenge и редиректа).

### Шаги

1. Перестаньте публиковать порт `web` напрямую наружу — пусть его видит только Caddy.
   В `docker-compose.yml` у сервиса `web` замените публикацию порта на внутреннюю:

   ```yaml
   web:
     # ports убрать/закомментировать — наружу торчит только Caddy
     expose:
       - "80"
   ```

2. Добавьте сервис `caddy` в `docker-compose.yml`:

   ```yaml
   caddy:
     image: caddy:2-alpine
     container_name: wolf-caddy
     ports:
       - "80:80"
       - "443:443"
     volumes:
       - ./Caddyfile:/etc/caddy/Caddyfile:ro
       - caddy_data:/data
       - caddy_config:/config
     depends_on:
       - web
     restart: unless-stopped
   ```

   И добавьте тома в секцию `volumes:`:

   ```yaml
   volumes:
     wolf_pgdata:
     caddy_data:
     caddy_config:
   ```

3. Создайте `Caddyfile` рядом с `docker-compose.yml`:

   ```caddyfile
   wolf.example.com {
       encode zstd gzip
       reverse_proxy web:80
   }
   ```

   Один блок `reverse_proxy` проксирует всё (SPA, `/api/`, `/docs/`) на nginx контейнера
   `web`, который уже разводит маршруты внутри. HTTP→HTTPS-редирект и выпуск сертификата
   Caddy делает сам.

4. Поднимите стек: `docker compose up -d`. При первом старте Caddy получит сертификат
   (это занимает секунды при корректной DNS-записи).

Продление — автоматическое, никаких cron/таймеров. Данные сертификатов сохраняются в
томе `caddy_data`.

---

## Вариант B — certbot + nginx (терминация TLS прямо в контейнере web)

Подходит, если вы не хотите вводить Caddy и предпочитаете классический nginx+certbot.
Здесь TLS терминирует сам nginx контейнера `web`.

### 1. Получить сертификат

Разово получите сертификат на хосте (вне контейнера) через certbot в режиме standalone
(на время выпуска порт 80 должен быть свободен) либо webroot:

```bash
# standalone — на момент выпуска остановите контейнер web (или certbot слушает 80 сам)
sudo certbot certonly --standalone -d wolf.example.com
```

Сертификаты появятся в `/etc/letsencrypt/live/wolf.example.com/`:
`fullchain.pem` и `privkey.pem`.

### 2. Пробросить сертификаты в контейнер web

В `docker-compose.yml` у сервиса `web` добавьте публикацию 443 и bind-mount каталога
Let's Encrypt (только чтение):

```yaml
web:
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./web/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    - /etc/letsencrypt:/etc/letsencrypt:ro
```

### 3. Включить TLS в `web/nginx.conf`

Добавьте HTTPS-сервер и редирект с 80 на 443. Блоки `location` (`/api/`, `/docs/`, `/`)
переносятся в 443-сервер как есть (см. текущий `web/nginx.conf`):

```nginx
# Редирект всего HTTP-трафика на HTTPS
server {
    listen 80;
    server_name wolf.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name wolf.example.com;

    ssl_certificate     /etc/letsencrypt/live/wolf.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/wolf.example.com/privkey.pem;

    # HSTS — браузер запоминает, что сюда только по HTTPS (год)
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://api:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /docs { return 301 /docs/; }
    location /docs/ {
        proxy_pass http://docs:80/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Применить без пересборки образа (nginx.conf смонтирован bind-mount'ом):

```bash
docker compose up -d --force-recreate web
```

### 4. Автопродление сертификата

certbot ставит systemd-таймер `certbot.timer` (или cron `/etc/cron.d/certbot`),
который продлевает сертификат дважды в сутки. После продления перечитайте конфиг nginx,
добавив deploy-hook:

```bash
sudo certbot renew --deploy-hook "docker compose -f /path/to/docker-compose.yml exec web nginx -s reload"
```

Проверить, что таймер активен: `systemctl list-timers | grep certbot`.

---

## Вариант C — self-signed (только для локального теста, без домена)

Для проверки HTTPS-конфига локально, без публичного домена. **Для боевого доступа не
годится** — браузеры покажут предупреждение о недоверенном сертификате.

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout privkey.pem -out fullchain.pem \
  -subj "/CN=localhost"
```

Смонтируйте эти файлы в контейнер `web` и укажите их в `ssl_certificate`/
`ssl_certificate_key` (как в варианте B). Чтобы убрать предупреждение, добавьте
сертификат в доверенные в ОС (`update-ca-certificates` в Linux, Связка ключей в macOS).

---

## Проверка

После включения HTTPS:

```bash
# Заголовки: должен ответить 200 и HTTP/2, при B/A — с HSTS
curl -I https://wolf.example.com

# HTTP должен редиректить на HTTPS (301)
curl -I http://wolf.example.com
```

Откройте приложение в браузере: замок в адресной строке, в консоли сети **нет**
предупреждений о смешанном контенте (SPA грузит `/api/v1` по тому же origin —
относительный путь, поэтому mixed content не возникает).

### Про `VITE_API_BASE`

По умолчанию `VITE_API_BASE = /api/v1` — относительный путь, при HTTPS всё работает без
правок. Если вы собирали фронт с **абсолютным** `VITE_API_BASE=http://ваш-домен/...`,
замените схему на `https://` и пересоберите `web`, иначе браузер заблокирует смешанный
контент.

---

## Итог: какой вариант выбрать

- Есть домен и нужен прод → **Вариант A (Caddy)**: меньше всего ручной работы, продление
  автоматическое.
- Уже используете nginx/certbot и хотите остаться на нём → **Вариант B**.
- Локальная проверка без домена → **Вариант C** (только тест).
