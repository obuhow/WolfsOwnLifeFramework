# Опечатка в заголовке README: «One» → «Own»

Status: resolved
Blocked by:

## Question

В главном `README.md` заголовок написан как `# WOLF - Wolf's One Life Framework`. Правильно — `Wolf's Own Life Framework` (WOLF = **W**olf's **O**wn **L**ife **F**ramework, аббревиатура раскрывается через «Own», а не «One»).

## Что сделать

1. `README.md` строка 1: `# WOLF - Wolf's One Life Framework` → `# WOLF - Wolf's Own Life Framework`.

Больше нигде опечатки нет — проверено: во всех остальных местах (`web/index.html`, `docs-site/index.html`, wayfinder-карта 0.1–0.2) уже стоит корректное «Own». Тикет узкий, ровно одна строка.

## Testing Decisions

- `grep -rn "Wolf's One" .` (исключая `.worktrees/`) — пустой вывод после правки.
- `grep -n "Wolf's Own Life Framework" README.md` — строка 1 совпадает.

## Out of Scope

- Ревизия остального содержимого README (статус `v0.1 / alpha`, порты и пр.) — не трогается, только опечатка в заголовке.

## Answer

Правка выполнена: `README.md` строка 1 — `Wolf's One Life Framework` → `Wolf's Own Life Framework`.

### Верификация
- `grep -rn "Wolf's One"` по репозиторию (исключая `node_modules`) — совпадения остались только
  в тексте самого тикета и в `spec.md` релиза 0.6, где опечатка цитируется как описание задачи.
  В исходниках и документации продукта — ноль.
- `grep -n "Wolf's Own Life Framework" README.md` → строка 1.
