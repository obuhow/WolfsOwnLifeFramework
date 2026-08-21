# 01 — Глобальная дизайн-система и базовые контролы

**What to build:** WOLF получает единый плоский визуальный язык «реестра»: белые поверхности, графитовая типографика, тонкие серые правила, бледно‑зелёный только для выполненного факта. Все базовые кнопки, поля, списки, диалоги, статусы загрузки/ошибки/пустого состояния и фокус‑состояния становятся общими, доступными и не используют прежние градиенты, тени, синие акценты, pill‑контролы или карточную стилистику.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Глобальные токены определяют утверждённые цвета, типографику, интервалы, правила и состояния; в целевых UI‑поверхностях нет тёплых градиентов, синей primary‑палитры, теней и скруглённых карточек.
- [x] Первичные действия, вторичные действия, поля, selects, checkbox/radio, таблицы, пустые/ошибочные/загружающиеся состояния и диалоги реализуют UI‑контракт из `spec.md` и доступны с клавиатуры.
- [x] Выполненная Запись времени и завершённый исход имеют бледно‑зелёный признак вместе с текстовым статусом; зелёный не становится generic CTA, а ошибки не зависят от красного цвета.
- [x] Фокус остаётся явно видимым, семантические labels сохранены, а дизайн проходит browser smoke на desktop и mobile.
- [x] Из обычного product flow убран фиксированный глобальный API‑footer, перекрывающий контент; диагностика API не маскируется как часть пользовательского экрана.

## Answer

Implemented shared register tokens and base controls, migrated the authenticated shell and login, normalized Today/Week status semantics and the existing Gantt surface, and removed the fixed API footer. Fresh verification: `npm run build`, `docker compose build web`, served asset hashes match `web/dist`, API health is `UP`, and authenticated browser/DOM checks confirmed white surface, zero-radius grid surface, pale-green completed record, visible focus rule and no footer. Mobile information architecture and grouped navigation are owned by ticket 02.