# WOLF Release Cancelled — Отменённые тикеты

Status: `wontfix`
Feature slug: `release-cancelled`
Tracker: local markdown (this file)

---

## Purpose

This release collects tickets that were explicitly cancelled (wontfix) during planning or implementation. They are preserved here for historical reference and to avoid re-creating them accidentally.

---

## Cancelled Tickets

### 16 — Импорт ICS → Записи времени (from release-0.1)

**Original Status:** `ready-for-agent` → **Cancelled: `wontfix`**

**Reason for cancellation:** 
- ICS import was planned as a one-shot feature for initial migration
- User decided to use xlsx import (release-0.2 ticket 11) as the primary historical data import mechanism instead
- ICS format parsing adds complexity (RRULE, timezones, recurring events) that doesn't align with the "quiet system" philosophy — the grid model works best with explicit 15-min entries
- If calendar import is needed later, it can be re-evaluated as a separate feature with proper scope

**Original scope (preserved for reference):**
- Upload ICS endpoint
- Maps into 15-min cells in user timezone
- Creates/links Дела by summary when useful
- UI entry point
- API-test import then GET time entries range

---

### 05 — Генератор демо-фикстур (from release-0.4-multiuser-demo)

**Original Status:** `open` → **Cancelled: `wontfix`**

**Reason for cancellation:**
- Единый фиксированный набор демо-фикстур заменён **тремя выбираемыми демо-профилями** — «Рабочий класс», «Мудрый фрилансер», «Свободный художник», — которые пользователь загружает при первом входе, выбрав описание своей ситуации
- Один набор фикстур показывает одну сторону WOLF; три профиля различаются структурно (недельная норма, число проектов, наполненность Банка идей) и демонстрируют разные сценарии использования
- Источник данных меняется: вместо генератора на Java — **декларативные файлы** (YAML/JSON) в репозитории + загрузчик со сдвигом дат относительно сегодня. Правка профиля не требует пересборки Java
- Решение принято при черчении карты `.scratch/wayfinder-releases-05-07/map.md`

**Что переходит дальше:** содержательная часть — состав фикстур и требование покрыть каждый пункт навигации непустым осмысленным состоянием — становится исходным материалом тикета «Содержание трёх демо-профилей» (`.scratch/wayfinder-releases-05-07/issues/05-demo-profiles-content.md`) и далее релиза 0.6.

**Original scope (preserved for reference):** `DemoFixtureGenerator.populate(User)` — детерминированный генератор от `LocalDate.now()`: 3 Области жизни, 4 Проекта, 12–15 Дел, записи времени за 14 дней с частично заполненным сегодня, недельные планы на две ISO-недели, 3 Цели, 5–6 связей Синергии, 4 Идеи, 3 Заметки (одна от Агента), зависимости проектов, непустой Бэклог.

---

### 06 — Demo Sandbox: эфемерная сессия, лимиты, сборка мусора (from release-0.4-multiuser-demo)

**Original Status:** `open` → **Cancelled: `wontfix`**

**Reason for cancellation:**
- Анонимный вход отменён целиком: **демо-стенд закрыт инвайт-кодами**, каждый гость получает настоящий постоянный аккаунт с загруженным демо-профилем
- Публичный `permitAll`-эндпоинт, создающий пользователей и пишущий в БД, — вектор заполнения диска; инвайт снимает эту угрозу без rate-limit, потолков и мониторинга
- Постоянный аккаунт лучше эфемерного продуктово: гость правит предзаполненные данные **в течение месяца**, а не теряет всё через 24 часа
- Владелец хранит персональные данные, поэтому контролируемый доступ по приглашению предпочтительнее анонимного (см. research «Правовая модель хранения чужих персданных и самохостинг»)
- Решение принято при черчении карты `.scratch/wayfinder-releases-05-07/map.md`

**Что отпадает вместе с тикетом:** эфемерные аккаунты `demo-<random>`, `account_type=REGULAR|DEMO`, `expires_at`, 24-часовой TTL, часовой GC по расписанию, флаг `wolf.demo.gc.enabled`, rate-limit по IP, потолок живых демо-аккаунтов, кнопка «Попробовать демо» на экране входа, плашка «Демо-режим · данные удалятся через N ч», тесты `DemoSessionApiIT` и `DemoGcIT`.

**Что сохраняется:** `UserPurgeService` из тикета 03 остаётся — он нужен админскому удалению пользователя (тикет 04) вместе с тестом на осиротевшие строки. Меняется только способ вызова: явное админское действие вместо планового задания.

**Original scope (preserved for reference):** `POST /api/v1/demo/session` (`permitAll`) → создание временного пользователя + фикстуры + JWT; лимиты `rate-per-ip-per-hour: 3`, `max-live-accounts: 200`, `ttl-hours: 24`; `@Scheduled` GC раз в час; фронтенд-кнопка на `/login` и плашка демо-режима в шапке.

**Снятые User Stories релиза 0.4 (13–22), сохранены дословно:**

13. Как посетитель, я хочу нажать «Попробовать демо» на экране входа и сразу оказаться внутри работающего WOLF, чтобы оценить систему без регистрации.
14. Как посетитель в демо, я хочу видеть заполненный «Сегодня» с записями времени за сегодня и остатком нормы, чтобы понять главную идею продукта за первые секунды.
15. Как посетитель в демо, я хочу видеть 3–4 реалистичных Проекта, Дела в разных состояниях и историю времени за прошедшие дни, чтобы Гант, Календарь и Бэклог были непустыми.
16. Как посетитель в демо, я хочу свободно создавать, менять и удалять что угодно, чтобы проверить систему в деле, а не смотреть на витрину.
17. Как посетитель в демо, я хочу видеть спокойную плашку «Демо-режим · данные удалятся через N ч» со ссылкой «Получить доступ», чтобы понимать статус сессии и знать следующий шаг.
18. Как посетитель, вернувшийся по той же ссылке позже, я хочу получить свежую демо-сессию, если прошлая истекла, чтобы не упереться в 401.
19. Как владелец WOLF, я хочу, чтобы демо-аккаунты автоматически удалялись вместе со всеми данными через 24 часа, чтобы база не росла и в системе не копился мусор.
20. Как владелец WOLF, я хочу ограничение частоты создания демо-сессий с одного адреса и потолок числа живых демо, чтобы публичная кнопка не стала вектором заполнения диска.
21. Как владелец WOLF, я хочу, чтобы фоновые агентские задачи не обрабатывали демо-аккаунты, чтобы не тратить LLM-бюджет на витрину.
22. Как посетитель в демо, я хочу, чтобы данные были датированы относительно сегодняшнего дня, чтобы в любой день демо выглядело живым, а не архивом.

Истории 14, 15 и 22 сохраняют силу как требования к **демо-профилям релиза 0.6** — меняется только носитель: постоянный аккаунт с загруженным профилем вместо эфемерной сессии.

---

## Future Considerations

**ICS-импорт.** If calendar import becomes necessary again, consider:
- CalDAV sync (live, not one-shot) — but this is explicitly out of scope per ADR-0003
- Google/Outlook OAuth import — requires user consent flow, not aligned with privacy-first local-first approach
- Manual ICS export → xlsx conversion → existing xlsx import pipeline

**Анонимный демо-доступ.** Если он понадобится снова (например, при выходе на публичный рынок), учесть:
- Изоляция арендатора (тикет 03 релиза 0.4) — обязательное предусловие, оно остаётся в силе
- Три демо-профиля из релиза 0.6 переиспользуются как содержимое эфемерного аккаунта: заново генератор писать не придётся
- `UserPurgeService` уже существует и покрыт тестом — GC достраивается поверх него
- Правовые последствия анонимного приёма данных отличаются от инвайт-модели: перепроверить выводы research перед открытием

---

## Related

- Release 0.1: `.scratch/release-0.1/spec.md` (original scope including ICS)
- Release 0.2: `.scratch/release-0.2/spec.md` (xlsx import as replacement, ticket 11)
- Release 0.4: `.scratch/release-0.4-multiuser-demo/spec.md` (многопользовательский режим; демо-песочница отменена)
- Wayfinder map 0.1–0.2: `.scratch/wayfinder-wolf/wolf-life-os-map.md` (import strategy)
- Wayfinder map 0.5–0.7: `.scratch/wayfinder-releases-05-07/map.md` (решение об отмене тикетов 05 и 06)