# Отображать версию WOLF в шапке

Status: resolved
Blocked by:

## Question

Показать номер текущей версии WOLF рядом с логотипом в шапке — сейчас версия нигде не видна пользователю (`web/package.json` держит `0.1.0`, `api/build.gradle` — `0.1.0-SNAPSHOT`, оба не совпадают с фактической релизной нумерацией 0.4/0.5/0.6, которой оперирует дорожная карта).

## Что сделать

1. Зафиксировать релизную версию как единый источник правды на сборку: поднять `web/package.json` → `"version": "0.6.0"` и `api/build.gradle` → `version = '0.6.0'` (убрать `-SNAPSHOT` для релизной сборки). Дальнейшие релизы обновляют оба файла синхронно — не предмет этого тикета, просто наблюдение для README/чеклиста релиза, если такой есть.
2. В `web/vite.config.js` (или создать, если конфиг сейчас неявный/дефолтный — проверить) добавить `define: { __APP_VERSION__: JSON.stringify(process.env.npm_package_version) }`, либо проще — читать версию через `import.meta.env` non работает для package.json напрямую, поэтому использовать `define` с явным чтением `web/package.json` в `vite.config.js` (`JSON.parse(fs.readFileSync('./package.json')).version`). Итог — глобальная константа `__APP_VERSION__`, доступная в любом компоненте без рантайм fetch.
3. В `web/src/App.vue` вывести версию рядом с текстом `WOLF` в обоих местах бренда:
   - Десктопная шапка: внутри `.brand-container`, под/рядом с `.brand-tagline` — новый элемент `<span class="brand-version">v{{ appVersion }}</span>`.
   - Мобильный drawer: аналогично внутри `.brand-container-sm` рядом с `.brand-tagline-sm`.
   - `appVersion` — вычисляемое свойство/константа в `<script setup>`, читающая `__APP_VERSION__`.
4. Стиль — тихий контракт 0.3: мелкий шрифт, `var(--muted-foreground)`/аналогичный приглушённый цвет уже используемый для `.brand-tagline`, без бейджа, без фона, без цвета-акцента.

## Testing Decisions

- `npm run build` в `web/` — собранный бандл (`web/dist/assets/*.js`) содержит строку `0.6.0` (grep по `dist/`).
- Гейт визуальных тикетов: сборка → пересоздание контейнеров → сверка хэшей с `web/dist` → аутентификация в браузере → инспекция DOM.
- Браузерная проверка (десктоп): в шапке рядом с логотипом `WOLF` видна `v0.6.0`.
- Браузерная проверка (мобильная ширина / открытый drawer): версия видна и в `.brand-container-sm`.
- Смена версии в `package.json`/`build.gradle` перед пересборкой отражается в шапке без правок `App.vue`.

## Out of Scope

- Отдельный API-эндпоинт версии бэкенда (`/actuator/info` или аналог) — версия берётся из фронтенд-сборки, не запрашивается с сервера.
- Автоматическое проставление версии из git-тега/CI на каждом коммите — версия правится вручную в `package.json`/`build.gradle` при подготовке релиза, как и раньше.
- История версий / changelog в UI — только текущий номер, без ссылки на список изменений.

## Answer

Версия выводится в обоих местах бренда, приёмка пройдена в браузере.

**Что сделано**

1. Единый источник правды на сборку: `web/package.json` → `"version": "0.6.0"`,
   `api/build.gradle` → `version = '0.6.0'` (без `-SNAPSHOT`).
2. `web/vite.config.js` — `define: { __APP_VERSION__: ... }` с чтением
   `package.json` через `fs.readFileSync`, как предписано пунктом 2.
3. `web/src/App.vue` — `const appVersion = __APP_VERSION__` в `<script setup>`;
   `.brand-version` в десктопной шапке (строка 259) и `.brand-version-sm`
   в мобильном drawer (строка 371).
4. Стиль тихий: `var(--muted-foreground)`, 11px / 10px, без фона и бейджа.

**Проверка (Testing Decisions)**

Скрипт `testing/verify_ticket08_version.py` (CDP, живой DOM) — `RESULT: PASS`:

```
login: True
bundles: ['http://localhost/assets/index-CAxgh1HL.js', 'http://localhost/assets/index-DxoVeYwc.css']
desktop .brand-version: {'found': True, 'text': 'v0.6.0', 'color': 'rgb(115, 115, 115)',
                         'fontSize': '11px', 'background': 'rgba(0, 0, 0, 0)', 'visible': True}
tagline color (для сверки тихого контракта): rgb(115, 115, 115)
mobile .brand-version-sm: {'found': True, 'text': 'v0.6.0', 'color': 'rgb(115, 115, 115)',
                           'fontSize': '10px', 'background': 'rgba(0, 0, 0, 0)', 'visible': True}
```

- Бандл содержит `0.6.0` — grep по отданному сервером `index-CAxgh1HL.js`.
- Сверка хэшей: отданное сервером (`index-CAxgh1HL.js`, `index-DxoVeYwc.css`)
  совпадает с `web/dist/assets/` — пересборка доехала до контейнера.
- Аутентификация в браузере выполнена под `obuhov`; шапка рендерится только
  при `v-if="token && !isOnboarding"`, поэтому grep по бандлу этот пункт не
  закрывает — нужен именно вход.
- Цвет версии совпадает с `.brand-tagline` (`rgb(115,115,115)`) — тихий
  контракт 0.3 соблюдён.

**Попутно починено**

`testing/cdp_driver.py`: `/json/new` вызывался методом POST, Chrome 151
отвечает `405 Method Not Allowed` (Chrome 111+ требует `PUT`). Без этой правки
браузерный гейт не запускался вообще. Зависимость `websockets` ставится в
`testing/.venv` через `uv` (на хосте нет pip).
