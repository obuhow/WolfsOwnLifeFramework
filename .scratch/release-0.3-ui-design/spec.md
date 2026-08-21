# WOLF Release 0.3 — Design System & UI Contract

Status: `ready-for-agent`
Feature slug: `release-0.3-ui-design`
Tracker: local markdown (this file)
Glossary: `CONTEXT.md`
Visual reference: `Minimal Web Application UI Mockups/WOLF - экран "Сегодня".dc.html`, interpretation **1a**
Depends on: `release-0.1` (existing calendar OS); defines the UI contract consumed by `release-0.2` (Methodology Layer)

---

## Problem Statement

The current WOLF interface is built from warm gradients, rounded cards, coloured button fills, large icon navigation and component-local visual rules. It does not yet express the product’s intended character: a calm, factual, private Life OS that records reality without applying pressure.

The user has chosen a distinct visual direction from the supplied Claude Design reference: **1a — a minimalist register**. For the most time-sensitive surfaces, “Сегодня” and the weekly Calendar, the primary reading model must be a precise 15-minute table on the left and a weekly project backlog on the right. The system must work on both desktop and mobile while retaining a coherent information architecture for the entire 0.2 product surface, including screens whose backend functionality will be implemented later.

A single written UI contract is needed before individual functional tickets create separate layouts, colours, terminology, and navigation patterns.

## Solution

Create a dedicated design release that introduces a global WOLF design system and applies it to the existing SPA. The release also specifies the UI contract for every planned Release 0.2 screen, without inventing missing backend behaviour.

The visual language is a **flat, typographic register**:

- white surfaces, graphite typography, and fine neutral-gray rules;
- no blue accents, warm gradients, elevated rounded cards, or traffic-light status system;
- pale green is reserved for a confirmed/completed fact and a low-emphasis successful outcome;
- primary actions are text actions with a thin underline; fields use a single lower rule;
- dense data pages preserve a shared coordinate system instead of simulating tables through unrelated cards.

The desktop shell uses a compact top navigation bar. On mobile it becomes a drawer with expandable menu groups and nested routes. “Сегодня” is always a direct, non-expandable destination.

## User Stories

1. As a WOLF user, I want every page to look like one quiet, precise system, so that switching between time, projects, and reflection does not create cognitive friction.
2. As a user, I want a white, graphite-first interface with fine rules, so that facts remain readable without dashboard-like visual noise.
3. As a user, I want pale green only to mark completed time or a completed outcome, so that status is visible without alarm colours or pressure.
4. As a user, I want no gradients, coloured action buttons, soft card shadows, or pill-shaped controls, so that WOLF feels like a personal register rather than a productivity dashboard.
5. As a user, I want text actions with restrained underlines and form inputs with a lower rule, so that controls do not dominate the content.
6. As a keyboard user, I want visible graphite focus rings and predictable focus order in menus, tables, forms and dialogs, so that the flat design remains accessible.
7. As a desktop user, I want a compact top navigation bar, so that the content keeps maximum vertical space.
8. As a mobile user, I want a drawer navigation with expandable groups and visible nested destinations, so that the full product structure is reachable without a horizontal crowd of links.
9. As a user, I want “Сегодня” to be a direct navigation item on desktop and mobile, so that the daily factual view is always one action away.
10. As a user, I want “Календарь” to group “Неделя” and “Месяц”, so that the two time horizons are clearly related.
11. As a user, I want “Планирование” to group “Диаграмма Ганта” and “Бэклог”, leaving room for a future Kanban entry, so that planning is discoverable without treating it as a single chart.
12. As a user, I want “Управление проектами” to group “Области жизни”, “Проекты”, “Дела”, and “Банк идей”, so that creating and maintaining initiatives has one home.
13. As a user, I want “Управление потоком” to group “Цели”, “Сферы жизни”, “Синергия”, “Утренний обход”, “Заметки / LLM Wiki”, and “Отчёт «Чек-лист»”, so that guidance and reflection are distinct from project administration.
14. As a user, I want “Сегодня” to show the date, the 15-minute grid, and day navigation as an information header instead of a decorative hero, so that the day begins with facts.
15. As a user, I want the “Сегодня” grid to be a left register with hour rules, quarter-hour rules, fixed time labels, and interval rows, so that the duration and exact position of time records remain legible.
16. As a user, I want planned entries to remain restrained and completed entries to receive a pale-green background, so that fact is distinguishable from plan without a red/green judgement system.
17. As a user, I want the current 15-minute interval marked with a graphite left rule, so that the present is locatable without animation or urgency.
18. As a user, I want night rows to remain hidden by default and explicitly revealable, so that the normal day is readable while the complete record remains available.
19. As a user, I want a right-side “Бэклог недели” beside the Today grid, so that current intention is visible next to recorded time.
20. As a user, I want the weekly backlog grouped by Project, so that the list shows the context of each Дело rather than one undifferentiated queue.
21. As a user, I want each project group in the weekly backlog to show `x / y ч` where `x` is completed fact in the current ISO week and `y` is planned hours, so that I can see reality against intention without a score or warning.
22. As a user, I want Delos without a Project to appear in a separate “Без проекта” group, so that valid routine work is neither hidden nor assigned a false context.
23. As a user, I want “Календарь → Неделя” to use the same 15-minute register grammar as Today, so that day and week differ in horizon, not visual language.
24. As a user, I want the weekly grid header and every day column to align to the same time-row geometry, so that an entry never visually drifts from its time label.
25. As a user, I want the weekly grid’s companion panel to keep the same grouped weekly backlog and `x / y ч` project summaries, so that planning context remains consistent.
26. As a user, I want “Календарь → Месяц” to use a quiet Outlook-like seven-column month grid, with a clear day number and compact factual entry labels, so that I can inspect the month without mistaking it for a slot editor.
27. As a user, I want the month view to place the weekly and monthly backlogs in the adjacent panel, so that period intention is visible but is not automatically scheduled.
28. As a user, I want “Планирование → Диаграмма Ганта” to use the same fine-rule register and a shared CSS grid for header and data rows, so that project names, weeks, plan and fact lanes are geometrically exact.
29. As a user, I want plan and fact lanes to remain visibly distinct without saturated chart colours, so that the diagram communicates data rather than performance judgement.
30. As a user, I want “Планирование → Бэклог” to be a full-page version of the grouped period backlog, so that I can manage week and month intentions outside the calendar.
31. As a user, I want project lists, project detail, Life Areas, Delos, and their forms to use section rules and inline editing patterns instead of floating cards, so that administrative work matches the register language.
32. As a user, I want the Project detail view to separate project facts, child projects, related Delos, plan/fact aggregates, notes, and future Synergy through labelled ruled sections, so that a Project remains a readable working context.
33. As a user, I want the Delo detail view to separate definition, execution mode, Project links, recurrence, time history, and notes through the same structure, so that a Delo is not visually confused with a time record.
34. As a user, I want the login page to be a minimal single-column form with no application navigation, so that access remains clear and private.
35. As a user, I want Settings to be a sequence of quiet fieldsets with explicit help text, so that settings never look like a gamified control panel.
36. As a user, I want a Goals page with priority order, weekly budget, current-week fact, metrics and linked Projects in a compact list/table, so that long-term direction is visible without daily pressure.
37. As a user, I want Life Spheres and Synergy to show positive, neutral and negative impacts with signed typography and labels rather than alarming colour semantics, so that trade-offs remain factual.
38. As a user, I want Ideas Bank to distinguish an idea from a Project through state and available actions, so that exploration is not accidentally turned into an obligation.
39. As a user, I want “Утренний обход” to show active Projects as a vertically ordered reading list with latest user notes, agent material styled by authorship, and the next Delos, so that I can perform a quiet morning scan.
40. As a user, I want Notes / LLM Wiki to make author, attachment/transcript, project or Delo context, date, tags and search/filter controls explicit, so that it works as a retrievable personal record.
41. As a user, I want the Checklist report to display dates and factual checklist/switch records without streaks, missed-day warnings or red absence markers, so that reflection stays non-punitive.
42. As a user, I want dialogs for creating a time record, Delo, Project, Idea or Note to be white, narrow, ruled and keyboard-operable, so that temporary work does not break the visual system.
43. As a user, I want empty, loading, error and successful-save states to describe the situation plainly and without blame, so that system feedback remains calm.
44. As a user, I want desktop layouts to preserve the grid-plus-aside relationship at sufficient width and collapse to a deliberate sequence on smaller displays, so that no information becomes inaccessible.
45. As a mobile user, I want 15-minute data to remain vertically scrollable with a sticky time axis/heading where technically appropriate, so that I can inspect a day without a compressed unusable grid.
46. As a mobile user, I want the right-side backlog to follow the time register below it, rather than competing horizontally, so that the primary temporal context remains usable.

## Implementation Decisions

### Product information architecture

The route and navigation contract is:

- **Сегодня** — direct route, no submenu.
- **Календарь** — expandable group: **Неделя**, **Месяц**.
- **Планирование** — expandable group: **Диаграмма Ганта**, **Бэклог**. A future **Канбан** entry belongs in this group but is not rendered as a disabled fake feature.
- **Управление проектами** — expandable group: **Области жизни**, **Проекты**, **Дела**, **Банк идей**.
- **Управление потоком** — expandable group: **Цели**, **Сферы жизни**, **Синергия**, **Утренний обход**, **Заметки / LLM Wiki**, **Отчёт «Чек-лист»**.
- **Настройки** — direct route.

On desktop the shell has the WOLF wordmark left, compact top-level navigation in the centre/remaining line, and the current user plus exit action at the right. Top-level grouped items expose their submenu by click and keyboard, using `aria-expanded`, Escape-to-close and click-away close. A route within a group marks both its parent and child active.

On mobile, the header has the WOLF wordmark and a menu trigger. The menu opens as a focus-trapped drawer. Top-level groups are buttons that expand/collapse their child links; the current group starts expanded. The drawer closes on route selection, Escape, overlay click, and explicit close. No horizontal navigation carousel is used on mobile.

Existing `#/week` and `#/gantt` paths are retained as compatibility redirects while the user-facing names/routes become Calendar Week and Planning Gantt according to Release 0.2. The detailed target route names must be aligned with the functional release before they become externally documented.

### Design tokens

Create a small global token layer consumed by every view; component-local hard-coded warm/green/blue palettes are prohibited.

| Token role | Value | Use |
|---|---:|---|
| Canvas / surface | `#FFFFFF` | page, panels, dialogs, inputs |
| Ink | `#1A1A1A` | headings, body, active navigation, primary actions |
| Muted ink | `#737373` | metadata, helper copy, inactive navigation |
| Faint ink | `#A3A3A3` | quiet secondary labels, placeholders |
| Rule | `#E5E5E5` | primary dividers, grid hour lines |
| Subrule | `#F2F2F2` | quarter-hour grid lines, row separators |
| Hover | `#F7F7F5` | interactive row hover |
| Done surface | `#F1F7F0` | completed time record and completed state only |
| Done rule / text | `#5D7A5E` | completed indicator when a contrast-bearing mark is required |
| Focus | `#1A1A1A` | 2px outline / underline focus indicator |

No saturated or semantic red, orange, or blue is part of the system. Errors use graphite text and an explicit `Ошибка:` label inside a thin graphite rule; destructive actions require a plain confirmation dialog and a textual verb, not a red button. Warning conditions are stated in prose and are never represented as failure colours.

### Typography, rhythm, and surfaces

- Font stack: `"Helvetica Neue", Helvetica, Arial, system-ui, sans-serif`; fallback must remain legible on Linux.
- Body: 13px / 1.45. Metadata: 11px / 1.4. Large page heading: 26px / 1.15, weight 600. Section heading: 11px / 1.2, weight 600, uppercase, `0.10em` tracking.
- Wordmark: 15px, weight 700, `0.14em` tracking.
- Use tabular numerals for every time, duration, week and `x / y ч` figure.
- Desktop content maximum: 1440px for non-grid management pages; calendar and planning surfaces may use full available width inside 24–28px page gutters.
- The standard content rule is a 1px line. Avoid shadows. Avoid radii; where browser affordance requires rounding, use at most 2px, never pills.
- White is the only default surface. A section is separated by rules and whitespace, not by a card background.
- Spacing scale: 4, 8, 12, 16, 24, 28, 40px. Page top/bottom: 26px/32px desktop; 18px/24px mobile.

### Controls and states

- **Primary action:** inline text in graphite with a 1px graphite underline; hover preserves contrast and adds a faint hover fill only where the target is row-sized.
- **Secondary action:** muted text; hover turns graphite. Icon-only controls are only permitted with visible `aria-label` and title, and must not be the sole visible action for destructive behaviour.
- **Completed action/state:** pale-green background/rule; green is not a generic primary CTA.
- **Inputs and selects:** white fill, no enclosing card, `border: 0; border-bottom: 1px solid var(--rule)`; graphite bottom rule on focus; labels are uppercase metadata. Native select affordance stays visible.
- **Checkbox/radio:** square and minimally styled; selected/checked state uses graphite, not green, unless the semantic value is completed.
- **Table row:** no radius; hover `#F7F7F5`; keyboard focus creates a 2px graphite inset/outline.
- **Dialog:** 380–560px width on desktop; white surface, 1px graphite border, 24px interior padding; no shadow; title, context line, vertically ruled fields, action row.
- **Toast:** avoid transient toast for ordinary save. Show a short inline status next to the action or heading; it must remain text-accessible. Errors persist until changed or dismissed.
- **Loading:** `Загрузка…` in muted text, with no full-screen blocking spinner unless the current action makes the affected form unavailable.
- **Empty:** section heading + one concise factual sentence + direct text action if a creation action exists.

### Shared page anatomy

Authenticated pages use this sequence:

1. compact shell header;
2. page information header: title, optional factual subtitle, contextual navigation/actions;
3. ruled content zones;
4. only if technically necessary, a low-height system footer. The global fixed API-status footer is removed from ordinary product reading flow; health belongs in a diagnostics/status location, not over page content.

Desktop breakpoints: regular two-column composition begins at 1024px. At 768–1023px, retain a top header but allow toolbars to wrap and side panels to reduce to 240px. Below 768px, switch to the drawer and stack main/aside content in reading order. At no breakpoint may a horizontal grid/table be silently clipped: it must have a labelled scroll container or a deliberate compact representation.

### Today and Calendar Week

Both views share a `time-register` presentation component or equivalent shared style contract:

- Header has the page title/date on the left and previous/next date (or week), night visibility and factual confirmation actions on the right. Actions remain text links, not filled buttons.
- Desktop body grid: `minmax(0, 1fr) 268px`; no gap between the time register and aside; the separating line is a 1px rule. The register owns its own horizontal scroll if needed, the aside does not overlay it.
- Time register rows use `grid-template-columns: 64px minmax(0, 1fr)` for Today. Time is right/left consistently aligned, muted, tabular. Each 15-minute row is 26px high in the 1a density. Hour rows use `--rule`; quarter-hour rows use `--subrule`.
- A time record begins with a 2px vertical status rule and has a range label, title and restrained meta. It preserves its full interval height/row span. Planned records use white or `#F7F8FA`; completed records use `--done-surface`; sleep uses a white/faint neutral treatment, not purple.
- Empty intervals are clickable but do not display fake labels. On hover they reveal the interaction through a faint surface change. The current interval uses a graphite left line only.
- The grid is not an evaluation display: no overdue red, no missed label, no alarm icon.
- The week view keeps a fixed time column and seven day columns. The header and body use the exact same explicit CSS Grid tracks. Calendar day headings show weekday, date and a factual daily total only where provided by the API.
- On mobile, the Today register remains one time column. The week view uses a labelled horizontal scroll region with a fixed time axis; it must not shrink each day below a readable width.

### Weekly backlog aside

The same component is used on Today, Calendar Week, Calendar Month companion panel, and Planning Backlog where the scope applies.

- Title: `Бэклог недели`; secondary ISO-week label is metadata.
- Group order follows the API/project ordering. Each group begins with a Project title as 13px graphite text. At the group’s right edge: `x / y ч`, tabular numerals; render `x / — ч` when plan is absent. `x` is completed time fact for the ISO week; `y` is the project’s planned current-week hours. The UI must not invent either value.
- Under the group heading, linked Delos are 13px text rows separated by subrules. Their execution mode and optional planned item hours are 11px muted metadata. A direct `+ Добавить дело` is last and muted.
- The `Без проекта` group is always separate and follows Project groups.
- The aside can filter by execution mode using a plain native select/underlined control. Filter controls must not hide the project hour summary.
- The aside has no coloured progress bar, completion percentage, countdown or warning label.

### Calendar Month

- Seven day columns (Mon–Sun) and 5–6 week rows; each day cell has a 1px rule and minimum readable height.
- Day number is small tabular type; out-of-month days are faint but remain structurally visible.
- Time record labels are one to two lines maximum with the start time and Delo/ad-hoc title. Completed entries use a pale-green left rule/background, not a full saturated fill.
- A day cell action opens that day/week; drag-and-drop scheduling is not part of the UI contract.
- On desktop the adjacent panel shows `Бэклог недели` followed by `Бэклог месяца`, each using the grouped project register. On mobile it follows the month grid.

### Planning: Gantt and Backlog

- User-facing page title: `Планирование`; submenu item: `Диаграмма Ганта`.
- The Gantt header, month spans, week headers, project rows and capacity rows use one explicit CSS Grid coordinate system. No inherited `.project-row` flex/card styling may affect Gantt rows.
- The left project column contains hierarchy marker, project title, life-area metadata and, when available, plan/fact summary text. It remains sticky inside the labelled horizontal scroll container.
- Each week cell contains two restrained strips: plan and fact. Their labels are tabular. Plan is graphite on a faint neutral band; fact is pale-green/green only when actual completed hours exist. Date range is a thin neutral rule, not a filled bar.
- Month and week headings use rules, never raised panels. Current week is identified by graphite typography/rule, not an alarm colour.
- Capacity appears beneath the same week tracks and shows plan / available hours and a neutral textual delta. It does not block edits or use error colours.
- Inline plan editing opens an underlined numeric field in the current cell. Keyboard Enter saves; Escape restores. Save/error text appears inline.
- Planning Backlog is a full-page period backlog with scope switch (`Неделя` / `Месяц`), period navigation, grouped project sections and explicit move-to-week action. It uses the shared backlog group component.

### Management pages

- **Области жизни:** one ruled list/table: colour is displayed only as a small square swatch if data requires it; edit/delete are text actions. Create/edit form follows lower-rule fields.
- **Проекты:** hierarchical register. Each row shows hierarchy, title, Area, optional dates and plan hours. Click opens detail; row actions remain text. Filtering sits in the page header.
- **Project detail:** title and area/date facts first; then ruled sections for description, plan/fact, child projects, Delos, notes, dependencies/forecast and Synergy when Release 0.2 provides them. No tile dashboard.
- **Дела:** ruled list with title, execution mode, primary/linked Projects and recurrence metadata. Import panel is a ruled expandable section with the complete schema hint and textual error response.
- **Delo detail:** ruled sections for definition, project links, recurrence, aggregate time history and notes.
- **Банк идей:** project-like register with idea title, category, short description, Synergy summary, state and explicit `Взять в работу` text action. It does not present plan hours or time records before promotion.
- **Settings:** fieldsets split by 1px rules: timezone/logical day/night, hour accounting mode, time capture mode, available weekly hours, mapping/import controls. Help text is factual and never behavioural pressure.
- **Login:** centred single-column register, wordmark, short privacy/control line, labels and underlined fields, explicit login error text. It has no global navigation.

### Flow-management pages (Release 0.2 UI contract)

These pages may initially use route-level scaffolding/read-only empty states until their API ticket is delivered, but their layout and copy contract is fixed here.

- **Цели:** priority-ordered table/list. Columns/fields: priority, goal, metric/current value, weekly budget, current-week fact, linked Projects. Editing is inline or dialog-based; budget change never creates a warning state.
- **Сферы жизни:** ruled reference list with name and concise description.
- **Синергия:** matrix/list view with subject (Project or Idea), Life Sphere, signed impact (`+`, `0`, `−`) and description. Use signs and text; do not encode negative impact with red.
- **Утренний обход:** ordered full-width list of active Projects. Each project shows title, latest user note, latest agent material clearly labelled `Агент`, and next Delos. Agent material uses a thin left graphite rule and author label rather than a bright special card. No notifications/badges.
- **Заметки / LLM Wiki:** search and factual filters above chronological ruled notes. Every note displays source context, author, timestamp, text/transcript and manual tags. Audio is an explicit attachment row with native control or download. Summary request is an explicit text action and response is a labelled section, never a chat-bubble surface.
- **Отчёт «Чек-лист»:** date-range controls; chronological table grouped by day; completion is pale green only for done items. Days without data remain a neutral row, not an absence failure. Switch records list the timestamp and target Delo/text without a negative “distraction” framing.

### Accessibility and responsive acceptance baseline

- Semantic `nav`, `main`, `header`, headings in order, real `button` for actions and menus, real `label` for every input.
- Visible focus state satisfies non-colour identification; keyboard navigation can open/close desktop menus, mobile drawer and dialogs.
- Background scroll locks while a drawer/dialog is open; focus returns to its trigger after closing.
- All interactive targets are at least 40px high/wide on mobile, even when the visual text treatment is thin.
- Text and rules meet practical contrast requirements; pale green is never the only completed-state signal — status text/title remains available.
- A UI-visible change is considered done only after build, Docker redeploy where applicable, authenticated browser check, hard refresh, and DOM geometry verification for calendar/Gantt grids. Build output or source inspection alone is insufficient.

## Testing Decisions

### What good tests are

A good design-system test observes user-visible behaviour: route/menu availability, semantic labels, menu state, keyboard operation, responsive structural state, text and status visibility, and calendar/Gantt geometry. It does not assert incidental implementation classes when a user-visible role, label, computed layout, or visual DOM boundary is available.

The existing authenticated HTTP API with Testcontainers PostgreSQL remains the product seam for functional data. UI design validation combines component/browser checks with an authenticated browser smoke against the deployed SPA. Browser/DOM verification is mandatory for geometry-sensitive pages.

### What is tested

- Navigation: desktop groups expose their approved children; Today stays direct; active parent/child state; compatibility redirects; mobile drawer focus trap, Escape, collapse/expand and route selection close.
- Token migration: no global warm gradient; no legacy blue primary; no rounded card/shadow rules used by target views; completed state uses the approved pale-green token; primary actions remain text/underline.
- Forms/dialogs: labels, focus order, keyboard submit/cancel, error text, dialog focus return.
- Today: exact time-register row count/labels for a logical day; 15-minute row and hour-rule geometry; planned/done state copy; grouped weekly backlog; group summaries receive API values rather than client-invented totals.
- Calendar Week: header day columns and each data row use the same computed tracks; actual browser geometry reports header-to-first-row deltas at or below 1px; horizontal mobile scroll remains reachable.
- Calendar Month: seven-column structure, out-of-month treatment, day routing/action, both backlogs and mobile stacking.
- Gantt: shared grid tracks; every week header aligns with the corresponding first project data cell at or below 1px delta; sticky project column does not overlap week data; plan/fact/capacity labels remain readable.
- Future 0.2 routes: each has the specified page heading, empty/loading state and navigation placement before API data work; later functional tickets add behavioural tests at the authenticated HTTP seam.
- Responsive browser checks at 1440px, 1024px, 768px and 375px verify desktop top nav, drawer switch, stacking order, overflow containers and no clipped primary controls.

### Prior art

- Existing Vue `TodayView`, `WeekView`, `GanttView`, `ProjectsView`, `DelosView`, `SettingsView`, and global `style.css` are migration targets.
- Existing Gantt alignment guidance in the `wolf-life-os` skill is mandatory: shared explicit CSS Grid tracks plus live DOM `getBoundingClientRect()` comparison after authenticated deployment.
- Existing current calendar workflows preserve all 0.1 behaviour: night hiding, 15-minute placement, confirmation, TimeEntry and Delo semantics. This release changes presentation/navigation, not the domain model.

## Out of Scope

- New 0.2 backend endpoints, database schema, business rules or LLM implementation.
- A fake Kanban page or disabled menu entry; Kanban remains a future Planning submenu addition.
- Design-token tooling beyond the Vue/CSS implementation needed by this repository (no Figma migration or external design-system service).
- Dark mode, themes, custom user colour selection, animation system or decorative illustration system.
- Drag-and-drop time scheduling and month-view editing.
- Push notifications, overdue warnings, streaks, gamification, Pomodoro or any pressure-oriented UI.
- Altering existing time accounting, night-hours, focus-session, import or Gantt calculation semantics without their owning functional release ticket.

## Further Notes

- The canonical source for the chosen visual interpretation is option **1a** in `Minimal Web Application UI Mockups/WOLF - экран "Сегодня".dc.html`; the later 1b/1c options were rejected as the primary language. 1c contributes only the mobile drawer concept.
- WOLF terminology remains authoritative: **Дело**, **Запись времени**, **Область жизни**, **Проект**, **Бэклог недели**, **Сфера жизни**, **Режим фиксации времени**, **Планирование**. Avoid “таска”, “задача” as the default UI noun, and corporate dashboard language.
- The system is intentionally quiet: copy names a fact, action, or forecast; it never tells the user they failed, are late, need to catch up, or should return to work.
- The project summary `x / y ч` is a contextual comparison, not a performance score. Missing plan renders as `—`, never `0`.
- This release is a UI contract for Release 0.2. Functional tickets may implement routes progressively, but may not introduce a competing visual language.
- Before claiming a visual ticket complete: build the web asset; recreate the web container if compose is running; compare served asset hashes with `web/dist`; authenticate in a real browser; hard refresh; verify target DOM/accessibility tree and geometry. For Gantt/Calendar, screenshot evidence alone is insufficient.
- The user selected a desktop top bar plus a mobile drawer, and explicitly requested expandable items with popup/submenu children. Desktop menus are therefore intentional, not a mobile-only compromise.
- Ticket breakdown is prepared separately under `.scratch/release-0.3-ui-design/issues/` only after the user approves its granularity and dependency edges.
```