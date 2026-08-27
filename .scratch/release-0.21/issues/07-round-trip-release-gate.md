# 07 — Round-trip, security and release gate

Status: `ready-for-agent`
Type: task
Blocked by: 05, 06

## What to build

Провести полный release gate: export → preview → apply → export, сравнение нормализованных листов, user isolation, large workbook behavior, legacy regression, API/UI smoke и no-secret scan.

## Acceptance criteria

- Round-trip preserves all supported fields, statuses and links.
- Другой пользователь не может читать preview, result или export.
- Invalid apply невозможен; failed apply оставляет базу без частичных записей.
- Relevant API suite and web build pass.
- Running Compose serves current UI assets and authenticated browser verification passes.

## Tests

- Full focused integration suite.
- Real browser DOM verification of synchronization page.
- Export content scan for secrets and service logs.

## Out of scope

Features after the confirmed 0.21 contract.

## Done definition

Release checklist complete and evidence appended here.

## Verification evidence

Not started.

## Answer

Not resolved.

## Status History

- `ready-for-agent`: initial ticket creation.

## Related docs

`.scratch/release-0.21/spec.md`, `docs/adr/0004-data-synchronization-backup-restore.md`.

## End

Status remains `ready-for-agent` until implementation evidence is added.

## Ticket ordering

01 contract → 02 export and 03 preview → 04 apply → 05 fixtures → 06 UI → 07 gate.

## Comments

### Частичные доказательства (релиз 0.6, попутно к тикету 0.21-06)

Тикет **остаётся `ready-for-agent`** — блокер `05` (fixtures and legacy
compatibility) не закрыт, и часть критериев ниже не проверялась. Ниже —
только то, что реально прогнано, чтобы следующий агент не повторял работу.

**Закрыто фактическим прогоном** (`testing/verify_ticket21_07_roundtrip.py`):

```
export:  200  14932 bytes
preview: 200  id: 2  status: VALID  applyAllowed: True  errors: 0
plan totals: create=0 update=0 skip=11 delete=0
apply:   200  status: APPLIED  created: 0  updated: 11  deleted: 0
RESULT: PASS — round-trip идемпотентен
```

- `Round-trip preserves ... links` — частично: выгруженный workbook принят
  обратно без единой ошибки валидации, все 11 строк опознаны как существующие
  (`create=0`, `delete=0`). Сравнение нормализованных листов второго экспорта
  с первым **не выполнялось**.
- `Другой пользователь не может читать preview/result` — **закрыто**:
  `admin` (id=1) на чужой preview `obuhov` (id=2) получает
  `HTTP 400 {"message":"Preview не найден"}` и на `/preview`, и на `/result`.
- `Relevant API suite and web build pass` — `npm run build` зелёный,
  `docker compose build api` зелёный.
- `Running Compose serves current UI assets and authenticated browser
  verification` — **закрыто**: хэши совпадают, браузерная приёмка UI пройдена
  (см. тикет 06).

**Не проверялось** — остаётся объёмом тикета:

- сравнение нормализованных листов `export -> ... -> export`;
- поведение на большом workbook (large workbook behavior);
- legacy regression через `LegacyWorkbookNormalizer`;
- `failed apply оставляет базу без частичных записей` (нужен намеренно битый
  файл);
- export content scan for secrets and service logs.
