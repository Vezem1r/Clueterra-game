CREATE TABLE IF NOT EXISTS badges (
    id               BIGINT       PRIMARY KEY,
    slug             VARCHAR(100) UNIQUE NOT NULL,
    image_url        VARCHAR(512),
    is_secret        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    grant_type       VARCHAR(20)  NOT NULL CHECK (grant_type IN ('TEMPLATE', 'CUSTOM', 'MANUAL')),
    grant_config     JSONB,
    display_order    INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    accent_color_key VARCHAR(32)  NOT NULL DEFAULT 'sky-core',
    effect_color_key VARCHAR(32)  NOT NULL DEFAULT 'amber-glow',
    category_id      BIGINT,
    CONSTRAINT badges_accent_color_key_check CHECK (accent_color_key IN (
        'violet-deep', 'violet-core',
        'violet-glow',
        'indigo-deep', 'indigo-core',
        'indigo-glow',
        'sky-deep', 'sky-core', 'sky-glow',
        'cyan-deep', 'cyan-core', 'cyan-glow',
        'emerald-deep', 'emerald-core',
        'emerald-glow',
        'amber-deep', 'amber-core', 'amber-glow',
        'rose-deep', 'rose-core', 'rose-glow'
    )),
    CONSTRAINT badges_effect_color_key_check CHECK (effect_color_key IN (
        'violet-deep', 'violet-core',
        'violet-glow',
        'indigo-deep', 'indigo-core',
        'indigo-glow',
        'sky-deep', 'sky-core', 'sky-glow',
        'cyan-deep', 'cyan-core', 'cyan-glow',
        'emerald-deep', 'emerald-core',
        'emerald-glow',
        'amber-deep', 'amber-core', 'amber-glow',
        'rose-deep', 'rose-core', 'rose-glow'
    ))
);

CREATE TABLE IF NOT EXISTS badge_translations (
    id                   BIGINT       PRIMARY KEY,
    badge_id             BIGINT       NOT NULL REFERENCES badges (id) ON DELETE CASCADE,
    locale               VARCHAR(5)   NOT NULL CHECK (locale IN ('en', 'ru', 'cs')),
    name                 VARCHAR(100) NOT NULL,
    description_unlocked TEXT         NOT NULL,
    description_locked   TEXT         NOT NULL,
    UNIQUE (badge_id, locale)
);

CREATE TABLE IF NOT EXISTS user_badges (
    id              BIGINT      PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id        BIGINT      NOT NULL REFERENCES badges (id) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by      VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' CHECK (granted_by IN ('SYSTEM', 'ADMIN')),
    is_acknowledged BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, badge_id)
);

CREATE INDEX IF NOT EXISTS idx_user_badges_unacknowledged ON user_badges (user_id) WHERE NOT is_acknowledged;

CREATE INDEX IF NOT EXISTS idx_user_badges_badge_id ON user_badges (badge_id);

CREATE TABLE IF NOT EXISTS badge_evaluation_log (
    id              BIGINT      PRIMARY KEY,
    badge_id        BIGINT      NOT NULL REFERENCES badges (id) ON DELETE CASCADE,
    evaluation_type VARCHAR(20) NOT NULL CHECK (evaluation_type IN ('NIGHTLY', 'MANUAL')),
    users_evaluated INTEGER     NOT NULL DEFAULT 0,
    users_granted   INTEGER     NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    duration_ms     BIGINT,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_badge_eval_log_badge_id ON badge_evaluation_log (badge_id);

CREATE OR REPLACE FUNCTION win_rate(played int, won int)
    RETURNS numeric AS
$$
SELECT ROUND(CAST(won AS numeric) / NULLIF(played, 0) * 100, 2);
$$ LANGUAGE sql IMMUTABLE;
