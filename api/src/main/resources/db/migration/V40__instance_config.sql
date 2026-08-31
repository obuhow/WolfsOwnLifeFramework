-- Release 1.1: глобальные настройки экземпляра (доступ по инвайтам)
CREATE TABLE instance_config (
    id INT PRIMARY KEY CHECK (id = 1),
    invite_access_open BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO instance_config (id, invite_access_open) VALUES (1, TRUE);
