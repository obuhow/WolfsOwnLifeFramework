# Сквозной аудит и обновление CONTEXT.md/AGENTS.md

Type: task
Status: open
Blocked by: 02, 03, 04, 05, 06, 07, 08, 09, 10

## Question

Финальная проверка, что destination карты достигнут:

- `grep -rl "private final.*Repository" --include='*Controller.java' src/main/java` пуст (кроме осознанно исключённых из Out of scope).
- Полный прогон `./gradlew test` — все `*ApiIT` зелёные.
- `find src/main/java -iname '*dto*'` показывает непустой набор пакетов по фичам.
- Обновить `CONTEXT.md`/`AGENTS.md`/`docs/adr/` при необходимости — зафиксировать итоговую архитектуру как «текущее состояние», а не только как ADR-решение.
- Обновить `.scratch/wayfinder-releases-05-07/map.md`-подобный статус — этот map.md получает финальную запись в Decisions so far.

## Answer

_(заполняется при резолве)_
