CREATE TABLE IF NOT EXISTS legal_acceptance_events (
    id                BIGINT                   PRIMARY KEY,
    user_id           BIGINT                   REFERENCES users (id) ON DELETE SET NULL,
    game_session_id   BIGINT                   REFERENCES game_sessions (id) ON DELETE SET NULL,
    acceptance_source VARCHAR(32)              NOT NULL,
    terms_version     VARCHAR(32)              NOT NULL,
    privacy_version   VARCHAR(32)              NOT NULL,
    accepted_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_legal_acceptance_scope CHECK (user_id IS NOT NULL OR game_session_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_legal_acceptance_events_user ON legal_acceptance_events (user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_acceptance_events_session ON legal_acceptance_events (game_session_id)
    WHERE game_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_acceptance_events_accepted_at ON legal_acceptance_events (accepted_at DESC);
