# Issue tracker: Local Markdown

Issues and specs (you may know a spec as a PRD) for this repo live as markdown files in `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- The spec is `.scratch/<feature-slug>/spec.md`
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01` — never a single combined tickets file
- Bugs live separately at `.scratch/<feature-slug>/bugs/<NN>-<slug>.md`, with their own numbering from `01` (see «Баги в resolved-тикетах» below). Planned work goes in `issues/`; discovered defects go in `bugs/`
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

## When a skill says "publish to the issue tracker"

Create a new file under `.scratch/<feature-slug>/` (creating the directory if needed).

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.

## Баги в resolved-тикетах

**Если тикет помечен `resolved`, а в его объёме найдена ошибка или недоделка — заводится баг. Молча дочинить чужой тикет нельзя.**

Правило действует, даже если починка занимает пять минут и ты уже сделал её попутно: без записи расхождение исчезает из истории, а `resolved` продолжает врать следующему агенту, который на него обопрётся.

Когда заводить:

- тикет `resolved`, но его чек-лист закрыт не полностью (типовой случай — поставлена одна половина вертикального среза: API есть, экрана нет);
- поведение противоречит тому, что тикет описывает;
- тест из Testing Decisions отсутствует или никогда не проходил.

Как заводить:

- новый файл `.scratch/<release>/bugs/<NN>-<slug>.md` — баги живут в **отдельной папке `bugs/`**, не в `issues/`, со **своей сквозной нумерацией** внутри релиза (первый баг релиза — `01`, независимо от того, сколько там тикетов). Релиз выбирается тот, где баг **обнаружен**, а не тот, где он возник;
- шапка: `Type: bug`, `Status: open`, `Найдено при:` (релиз + тикет), `Регрессия в:` (релиз + тикет + его текущий статус);
- в теле — доказательство расхождения (коммит, `git show --stat`, вывод grep/curl), а не пересказ впечатления;
- отдельный раздел «Почему это важно» — что ломается ниже по течению;
- чек-лист «Что сделать» + пункт «решить, оставлять ли `resolved` у исходного тикета» — статус чужого тикета меняет владелец, не находящий агент;
- если баг попутно закрыт в рамках текущего тикета, это фиксируется разделом «Как закрыт» со ссылкой на ветку и коммит; сам баг всё равно заводится.

Найденное расхождение дополнительно упоминается в `## Answer` тикета, при работе над которым оно всплыло, — со ссылкой на номер заведённого бага.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` — the Notes / Decisions-so-far / Fog body.
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, numbered from `01`, with the question in the body. A `Type:` line records the ticket type (`research`/`prototype`/`grilling`/`task`); a `Status:` line records `claimed`/`resolved`.
- **Blocking**: a `Blocked by: NN, NN` line near the top. A ticket is unblocked when every file it lists is `resolved`.
- **Frontier**: scan `.scratch/<effort>/issues/` for files that are open, unblocked, and unclaimed; first by number wins. `bugs/` is NOT part of the frontier — a bug is picked up only when the user explicitly schedules it.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
