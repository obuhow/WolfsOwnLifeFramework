CREATE TABLE IF NOT EXISTS life_sphere (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    color VARCHAR(7),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_life_sphere_user_sort ON life_sphere (user_id, sort_order);

CREATE TABLE IF NOT EXISTS synergy (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    idea_id BIGINT,
    sphere_id BIGINT NOT NULL REFERENCES life_sphere(id) ON DELETE CASCADE,
    impact VARCHAR(10) NOT NULL CHECK (impact IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, sphere_id),
    UNIQUE (idea_id, sphere_id),
    CHECK ((project_id IS NOT NULL AND idea_id IS NULL) OR (project_id IS NULL AND idea_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_synergy_user ON synergy (user_id);
CREATE INDEX IF NOT EXISTS idx_synergy_project ON synergy (project_id);
CREATE INDEX IF NOT EXISTS idx_synergy_idea ON synergy (idea_id);
CREATE INDEX IF NOT EXISTS idx_synergy_sphere ON synergy (sphere_id);

-- Seed 9 life spheres (idempotent)
INSERT INTO life_sphere (user_id, name, sort_order, color, archived, created_at, updated_at)
SELECT u.id, s.name, s.sort_order, s.color, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM "user" u
CROSS JOIN (
    VALUES
        ('Здоровье', 0, '#EF4444'),
        ('Навык QA Java', 1, '#3B82F6'),
        ('Навык музыканта', 2, '#8B5CF6'),
        ('Общение на расстоянии', 3, '#06B6D4'),
        ('Мотивация к делам', 4, '#F59E0B'),
        ('Ресурсы/деньги', 5, '#10B981'),
        ('Ресурсы/время', 6, '#84CC16'),
        ('Открытие новых ходов', 7, '#EC4899'),
        ('Независимость', 8, '#6366F1')
) AS s(name, sort_order, color)
WHERE NOT EXISTS (
    SELECT 1 FROM life_sphere ls WHERE ls.user_id = u.id AND ls.name = s.name
);