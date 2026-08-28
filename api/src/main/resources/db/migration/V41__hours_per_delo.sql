-- Release 0.8, ticket 01: «Часов на одно Дело» — оценка объёма проекта без totalPlanHours.
-- NOT NULL + DEFAULT 1.5: существующие строки получают дефолт при добавлении колонки.

ALTER TABLE "user" ADD COLUMN hours_per_delo NUMERIC(5, 2) NOT NULL DEFAULT 1.5;
