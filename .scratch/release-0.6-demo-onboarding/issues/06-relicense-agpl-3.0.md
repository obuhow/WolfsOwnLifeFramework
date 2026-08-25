# Смена лицензии на AGPL-3.0 + заголовок лицензии в каждом файле

Status: open
Blocked by:

## Question

Перевести проект с MIT (`LICENSE.md`, MIT License, Copyright (c) 2025 Pavel Obukhov) на **GNU Affero General Public License v3.0**. AGPL требует, чтобы в каждом исходном файле проекта присутствовал стандартный заголовок лицензии со ссылкой на copyleft и copyright-строкой.

## Что сделать

1. Заменить содержимое `LICENSE.md` (сейчас MIT) на полный официальный текст **AGPL-3.0** (`GNU AFFERO GENERAL PUBLIC LICENSE Version 3, 19 November 2007`, дословно с gnu.org/licenses/agpl-3.0.txt). Опционально переименовать `LICENSE.md` → `LICENSE` (plain text), если так удобнее для распознавания GitHub/линтерами — решить при реализации, но текст должен быть дословным.
2. Обновить упоминания лицензии в README и метаданных сборки, чтобы они говорили AGPL-3.0, а не MIT:
   - `README.md` строка со «**лицензия:** open source for personal use (AGPL-3.0)» — уже упоминает AGPL, но привести формулировку в согласие с новым `LICENSE` (убрать «for personal use», т.к. AGPL — это не personal-use лицензия).
   - `api/build.gradle` / `api/build.gradle.kts` и `web/package.json` — проверить поле `license`/`licenses`; если стоит `MIT`, заменить на `AGPL-3.0-only` (SPDX id).
3. Добавить в начало **каждого исходного файла проекта** стандартный AGPL-заголовок. Точный текст берётся из раздела «How to Apply These Terms to Your New Programs» самой AGPL-3.0, в сокращённом виде:
   ```
   WOLF — Wolf's Own Life Framework
   Copyright (C) 2025 Pavel Obukhov

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public License
   along with this program. If not, see <https://www.gnu.org/licenses/>.
   ```
   Заголовок оформляется комментарием, синтаксически корректным для типа файла: `/* ... */` (или построчно `//`) для `.java`/`.js`/`.ts`, `<!-- ... -->` для `<template>`-обёрток нельзя внутри `.vue` перед `<template>` — для `.vue` использовать HTML-комментарий `<!-- ... -->` в самом верху файла (валиден для SFC) либо блок `<!-- ... -->` над `<script>`; при реализации выбрать вариант, который не ломает Vite/ESLint и не попадает в отрендеренный DOM.

## Область файлов (что считать «каждым файлом проекта»)

Только исходники основного дерева, НЕ `.worktrees/`, НЕ сгенерированное, НЕ вендор:
- `api/src/**/*.java` (~186 файлов)
- `web/src/**/*.vue` (~36), `web/src/**/*.{js,ts}` (~3)
- **Исключить**: `**/build/**`, `**/dist/**`, `**/node_modules/**`, `**/.gradle/**`, `**/.worktrees/**`, автогенерируемые файлы (Gradle wrapper, Vite manifest), сторонние минифицированные бандлы.
- SQL-миграции (`api/src/main/resources/db/**`) и конфиги (`application.yml`) — по решению реализатора; заголовок в `.sql`/`.yml` через `--`/`#` не обязателен, но допустим. Данные-фикстуры (`assets/profiles/*.json`) — JSON заголовки не поддерживает, пропустить.

Идемпотентность: скрипт добавления заголовка должен проверять наличие маркера (`Wolf's Own Life Framework` / `GNU Affero`) в первых N строках и не дублировать заголовок при повторном прогоне.

## Testing Decisions

- `grep -L "GNU Affero"` по целевому набору `api/src/**/*.java` и `web/src/**/*.{vue,js,ts}` — пустой вывод (заголовок есть везде, кроме явных исключений).
- Повторный прогон скрипта добавления заголовка — 0 изменённых файлов (идемпотентность).
- Сборка не сломана: `./gradlew build` в `api/` и `npm run build` в `web/` проходят с добавленными заголовками (комментарии не влияют на компиляцию/бандл).
- Заголовок `.vue` не протекает в DOM: инспекция собранной страницы — комментарий лицензии не виден в отрендеренном HTML пользователю.
- `LICENSE` содержит дословный текст AGPL-3.0 (сверка первой строки `GNU AFFERO GENERAL PUBLIC LICENSE` и длины файла ~34 KB).
- GitHub распознаёт лицензию как «GNU Affero General Public License v3.0» (проверка после пуша, если применимо).

## Out of Scope

- Простановка заголовков в файлах внутри `.worktrees/` — это временные рабочие копии, приводятся в согласие при их слиянии, не здесь.
- Юридическая консультация о совместимости AGPL с зависимостями проекта (Spring, Vue и т.д.) — отдельный вопрос, не блокирует техническую простановку.
- CLA / DCO, требование подписи контрибьюторов — вне объёма релиза 0.6.
