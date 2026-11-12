CREATE TABLE IF NOT EXISTS app_settings_audit (
    id                  BIGSERIAL                PRIMARY KEY,
    change_batch_id     UUID                     NOT NULL,
    setting_key         VARCHAR(100)             NOT NULL,
    old_value           VARCHAR(1000),
    new_value           VARCHAR(1000)            NOT NULL,
    changed_by_user_id  BIGINT,
    changed_by_email    VARCHAR(255),
    changed_by_username VARCHAR(100),
    changed_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_app_settings_audit_user
        FOREIGN KEY (changed_by_user_id) REFERENCES cl_api_schema.users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_app_settings_audit_changed_at
    ON app_settings_audit (changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_app_settings_audit_batch
    ON app_settings_audit (change_batch_id);
