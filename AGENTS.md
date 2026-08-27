# WOLF

## Agent skills

### Issue tracker

Issues live as local markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.

## Active wayfinder maps

`.scratch/wayfinder-release-1.0-public-beta/map.md` — релиз 1.0: закрытая бета на публичном домене (боевой контур, лендинг, инвайты, инструкция тестерам, деки). **Жёсткий срок: 5 сентября 2026.**

`.scratch/wayfinder-release-0.9-layered-arch/map.md` — релиз 0.9: миграция `wolf-api` на слоистую архитектуру (тонкий MVCS + точечные Ports&Adapters). Идёт параллельно; по решению владельца должна быть закрыта до старта 1.0, но при цейтноте режется первой (см. тикет 11 карты 1.0).

Предыдущие карты: `.scratch/wayfinder-releases-05-07/map.md` (релизы 0.5–0.7); `.scratch/wayfinder-wolf/wolf-life-os-map.md` (релизы 0.1–0.2).

