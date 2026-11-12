CREATE TABLE IF NOT EXISTS app_settings (
    key        VARCHAR(100)             PRIMARY KEY,
    value      VARCHAR(1000)            NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (key, value)
VALUES ('registration_enabled', 'false')
ON CONFLICT DO NOTHING;
