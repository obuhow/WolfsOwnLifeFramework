# 21-followup: восстановить JWT на data-sync endpoints

- **release**: 0.21
- **status**: ready-for-agent
- **labels**: security, tech-debt, carried-from-0.21
- **origin**: перенос из feature/21-data-sync (мердж 0.21)

## Контекст
В ходе релиза 0.21 для сквозной приёмки импорта
`docs/import/wolf-data-v0.21-TEST-filled.xlsx` временно ослаблена авторизация
data-sync, чтобы preview/apply/export можно было гонять curl'ом без токена.

## Что именно ослаблено (вернуть к JWT)
1. `SecurityConfig.java` — `.requestMatchers("/api/v1/data-sync/**").permitAll()`
   заменить на `.authenticated()`.
2. `DataSyncImportController` — методы preview/getPreview/apply/result/plan
   резолвят `userRepository.findByUsername("admin")`. Вернуть параметр
   `Authentication` и `currentUser(authentication)`.
3. `DataSyncExportController` — то же: `export(...)` резолвит "admin".
   Вернуть `Authentication`.
4. `DataLoader` (config) сеет пользователя `admin/admin` при старте — оставить
   только для dev-профиля или убрать перед prod.

## Критерий приёмки
- Все data-sync endpoints возвращают 401/403 без валидного Bearer-токена.
- С токеном preview→apply→export работают как в 0.21 (round-trip идемпотентен).
- Интеграционный тест: unauthenticated → 401; authenticated → 200.

## Оставшееся из 0.21 (не блокеры, вынесено сюда же)
- Browser/DOM smoke UI `/data-sync` (требовал ручного remote-debugging Chrome).
- Fixtures: закоммитить приёмочный workbook как round-trip regression в CI.
- Приёмочный файл был поправлен: `goal_income` priority 1→4
  (конфликт UNIQUE(user_id, priority)); бэкап оригинала лежит рядом
  как `.bak-*`.
