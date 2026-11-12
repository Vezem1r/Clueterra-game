CREATE TABLE IF NOT EXISTS hint_usage_log (
    id           BIGINT      PRIMARY KEY,
    hint_id      BIGINT      NOT NULL,
    country_id   BIGINT      NOT NULL,
    used_date    DATE        NOT NULL,
    game_mode    VARCHAR(20) NOT NULL,
    challenge_id BIGINT,
    CONSTRAINT fk_usage_hint FOREIGN KEY (hint_id) REFERENCES hints (id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_country FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE CASCADE,
    CONSTRAINT uq_usage_unique UNIQUE (hint_id, used_date, game_mode)
);

CREATE INDEX IF NOT EXISTS idx_usage_hint_id ON hint_usage_log (hint_id);

CREATE INDEX IF NOT EXISTS idx_usage_country_id ON hint_usage_log (country_id);

CREATE INDEX IF NOT EXISTS idx_usage_date ON hint_usage_log (used_date DESC);

CREATE INDEX IF NOT EXISTS idx_usage_game_mode ON hint_usage_log (game_mode);

CREATE INDEX IF NOT EXISTS idx_usage_hint_date_mode ON hint_usage_log (hint_id, used_date DESC, game_mode);

CREATE INDEX IF NOT EXISTS idx_usage_country_date ON hint_usage_log (country_id, used_date DESC);

COMMENT ON TABLE hint_usage_log IS 'Tracks hint usage to prevent repetition';

COMMENT ON COLUMN hint_usage_log.used_date IS 'Date when the hint was used';

COMMENT ON COLUMN hint_usage_log.game_mode IS 'Game mode: DAILY, PRACTICE, etc.';

COMMENT ON COLUMN hint_usage_log.challenge_id IS 'Optional reference to daily challenge';

COMMENT ON CONSTRAINT uq_usage_unique ON hint_usage_log IS 'Prevents same hint being used twice on same date in same mode';
