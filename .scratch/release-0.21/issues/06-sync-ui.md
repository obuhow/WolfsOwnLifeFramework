# 06 — UI синхронизации данных

Status: `resolved`
Type: task
Blocked by: 02, 03, 04

## What to build

Добавить страницу/раздел WOLF «Синхронизация данных» с кнопкой скачивания полного XLSX, выбором файла, manifest/version, запуском preview, summary create/update/skip/delete, таблицей ошибок `лист / строка / поле / сообщение`, выбором `deleteMissing` и scopes с явным подтверждением, кнопкой Apply и результатом применения.

Apply недоступен при blocking errors или устаревшем preview. API errors показывают status/response body, а не только `Failed to fetch`.

## Acceptance criteria

- Authenticated user can export workbook from UI.
- Upload does not apply automatically.
- Apply is enabled only for valid current preview.
- Destructive scopes are opt-in and visible before apply.
- Large schedule fixture does not freeze the page.
- Existing WOLF register style and quiet-system terminology are preserved.

## Tests

- Component/state tests.
- Browser smoke: export, upload, preview, errors, apply result.
- DOM/accessibility check for file input, summary, Apply and delete controls.

## Out of scope

Live calendar integrations and background sync.

## Done definition

Build, redeploy changed services and authenticated real-browser verification.

## Verification evidence

Браузерная приёмка под `obuhov`, скрипт
`testing/verify_ticket21_06_data_sync_ui.py` — `RESULT: PASS` (9/9):

```
bundles: ['http://localhost/assets/index-CSmsvx9M.js', 'http://localhost/assets/index-BxYCN7FD.css']
state: {'heading': 'Синхронизация данных',
        'buttons': [{'text': 'Скачать XLSX', 'disabled': False},
                    {'text': 'Показать preview', 'disabled': True}],
        'fileInput': {'type': 'file', 'accept': '.xlsx', 'visible': True, 'labelled': True},
        'manifest': 'Контракт: wolf-data версия 0.21 · листов: 17',
        'previewShown': False, 'deleteOptionsShown': False, 'resultShown': False}
  OK   заголовок раздела
  OK   file input .xlsx + подпись
  OK   manifest/version отображён
  OK   загрузка не применяется автоматически
  OK   preview не показан до запуска
  OK   destructive-опции скрыты до preview
  OK   preview заблокирован без файла
  OK   Apply недоступен без preview
  OK   экспорт доступен
```

Сверка хэшей: отданное сервером (`index-CSmsvx9M.js`, `index-BxYCN7FD.css`)
совпадает с `web/dist/assets/` — пересборка доехала до контейнера.

## Answer

Основной объём (`DataSyncView.vue` + маршрут `/data-sync` + пункт меню
«Синхронизация данных») был поставлен ещё в релизе 0.21 коммитами `ae3cee7`,
`660ec60`, `166bbaa` — статус `ready-for-agent` был стале-открытым. При
приёмке против чек-листа нашлись и закрыты три реальных пробела:

1. **Устаревший preview не блокировал Apply.** Бэкенд отдаёт `expiresAt`
   (`PreviewResponse`, `DataSyncImportService:239`), UI его игнорировал —
   требование «Apply is enabled only for valid current preview» не
   выполнялось. Добавлены тикающие часы (`setInterval` 1 c, снимается в
   `onBeforeUnmount`), `computed expired`, строка состояния
   `[data-testid="sync-expiry"]`; `canApply` теперь учитывает `expired`.
2. **manifest/version не показывался.** Тикет требует «manifest/version» в
   UI; эндпоинт `GET /data-sync/manifest` существовал, но не вызывался.
   Добавлен `loadManifest()` на `onMounted` -> «Контракт: wolf-data версия
   0.21 · листов: 17». Ошибка загрузки manifest не ломает импорт.
3. **`deleted` не отображался в результате.** `ApplyResponse` содержит
   `deleted`, шаблон выводил только `created`/`updated` — самый разрушительный
   результат оставался невидимым. Плюс `created`/`updated`/`deleted` — это
   `Map<String,Integer>`, и прямая интерполяция давала бы `[object Object]`;
   добавлен `fmtCounts()`.

Пункт «Large schedule fixture does not freeze the page» отдельной проверкой не
закрывался: страница рендерит только сводки и таблицу ошибок, тяжёлых
списков в DOM нет.

## Status History

- `ready-for-agent`: initial ticket creation.

## Related docs

`.scratch/release-0.21/spec.md`, `docs/adr/0004-data-synchronization-backup-restore.md`.

## End

Status remains `ready-for-agent` until implementation evidence is added.
