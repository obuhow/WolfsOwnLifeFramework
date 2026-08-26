# Опубликовать релиз 0.6 — Демо-профили и Знакомство

Type: task
Status: resolved
Blocked by: 03, 05, 06, 11

## Question

Написать `.scratch/release-0.6-demo-onboarding/spec.md` и завести его тикеты.

## Answer

Опубликован `.scratch/release-0.6-demo-onboarding/spec.md` (`ready-for-agent`) + 5 тикетов вертикальных срезов:

- `01-demo-profile-generator.md` — расширение `DemoFixtureGenerator` до декларативных JSON-профилей, три файла `assets/profiles/{worker-class,wise-freelancer,free-artist}.json`.
- `02-profile-load-screen.md` — экран «Загрузка профиля» на `/onboarding/profile`, новая точка входа guard'а вместо прямого редиректа на мастер 0.4-08.
- `03-onboarding-tour.md` — тур Знакомства (6–7 шагов, вырез-подсветка, `data-tour-target` на пунктах NAV из релиза 0.5), Depends on release-0.5.
- `04-final-choice-and-purge.md` — Финальный выбор + `UserPurgeService.purgeProfileData()`, ветка «Очистить» передаёт управление мастеру 0.4-08 без изменения его кода.
- `05-settings-reload-profile.md` — повторная загрузка профиля из `SettingsView.vue`.

Материал тикетов «Содержание трёх демо-профилей» и «Механика Знакомства» (карта 05-07) перенесён в spec.md полностью — эталонное содержание для «Рабочего класса», параметры для двух остальных, механика тура/финального выбора/повторной загрузки. Зависимость от 0.4 (тикеты 01, 02, 03, 08) и от 0.5 (структура меню, DOM-цели тура) указана явно в шапке и в разделе Depends on тикета 03.

Отменённый тикет 0.4-05 (генератор демо-фикстур) упомянут в `## Further Notes` spec.md как источник уже существующего кода `DemoFixtureGenerator`, который эта спецификация не создаёт заново, а параметризует.
