# Паттерн, ADR и референсная миграция

Type: task
Status: open
Blocked by:

## Question

Зафиксировать точный, воспроизводимый паттерн тонкого MVCS + точечных портов (вариант D, см. `map.md`) как ADR, и провести миграцию одной небольшой референсной фичи (`Idea` — `IdeaController`, ~4 файла, уже есть `IdeaAlreadyPromotedException`) по этому паттерну, чтобы все последующие тикеты копировали проверенный образец, а не изобретали вариации.

Конкретно нужно решить и зафиксировать примерами кода:

- Точное имя и расположение `dto/`-пакета, naming convention для record'ов (`CreateGoalRequest` → куда, `GoalResponse` → куда).
- Как выглядит статический фабричный метод маппинга на record (`GoalResponse.from(Goal goal)` vs конструктор vs отдельный `GoalMapper`).
- Куда переезжает `@Transactional` — остаётся на методах контроллера или переезжает в Service (рекомендация: в Service).
- Как Service получает текущего `User` — остаётся ли `Authentication authentication` параметром контроллера, который резолвит User и передаёт в Service, или Service сам берёт из `SecurityContextHolder`.
- Где живут доменные исключения (`DuplicateGoalPriorityException` и т.п.) — остаются как есть рядом с моделью.
- Публикуется как `docs/adr/0005-layered-architecture-mccv.md` (или следующий свободный номер).

## Answer

_(заполняется при резолве)_
