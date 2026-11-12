CREATE TABLE IF NOT EXISTS user_profiles (
    user_id                                      BIGINT                   PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name                                 VARCHAR(50),
    avatar_url                                   VARCHAR(255),
    total_xp                                     BIGINT                   NOT NULL DEFAULT 0,
    current_level                                INTEGER                  NOT NULL DEFAULT 1,
    challenges_played                            INTEGER                  NOT NULL DEFAULT 0,
    challenges_won                               INTEGER                  NOT NULL DEFAULT 0,
    current_streak                               INTEGER                  NOT NULL DEFAULT 0,
    best_streak                                  INTEGER                  NOT NULL DEFAULT 0,
    favorite_category                            VARCHAR(30),
    created_at                                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    streak_freezes                               INTEGER                  NOT NULL DEFAULT 0,
    streak_freeze_used_at                        TIMESTAMP WITH TIME ZONE,
    countries_played                             INTEGER                  NOT NULL DEFAULT 0,
    countries_solved                             INTEGER                  NOT NULL DEFAULT 0,
    perfect_runs                                 INTEGER                  NOT NULL DEFAULT 0,
    favorite_difficulty                          VARCHAR(10),
    last_completed_at                            TIMESTAMP WITH TIME ZONE,
    streak_freezes_used_since_completion         INTEGER                  NOT NULL DEFAULT 0,
    streak_freezes_acknowledged_since_completion INTEGER                  NOT NULL DEFAULT 0,
    CONSTRAINT chk_streak_freezes_non_negative CHECK (streak_freezes >= 0),
    CONSTRAINT chk_streak_freezes_used_since_completion_non_negative CHECK (streak_freezes_used_since_completion >= 0),
    CONSTRAINT chk_streak_freezes_acknowledged_since_completion_range CHECK ( streak_freezes_acknowledged_since_completion >= 0 AND streak_freezes_acknowledged_since_completion <= streak_freezes_used_since_completion )
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_xp ON user_profiles (total_xp DESC);

CREATE INDEX IF NOT EXISTS idx_user_profiles_level ON user_profiles (current_level DESC);

CREATE INDEX IF NOT EXISTS idx_user_profiles_leaderboard
    ON user_profiles (total_xp DESC, user_id ASC);

CREATE TABLE IF NOT EXISTS game_sessions (
    id                            BIGINT                   PRIMARY KEY,
    user_id                       BIGINT                   REFERENCES users (id) ON DELETE SET NULL,
    guest_token                   VARCHAR(64),
    challenge_id                  BIGINT                   NOT NULL REFERENCES cl_schema.daily_challenges (id),
    challenge_level               VARCHAR(10)              NOT NULL,
    status                        VARCHAR(20)              NOT NULL DEFAULT 'IN_PROGRESS',
    current_country_idx           INTEGER                  NOT NULL DEFAULT 0,
    started_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at                  TIMESTAMP WITH TIME ZONE,
    total_xp_earned               INTEGER                  NOT NULL DEFAULT 0,
    max_hints_per_country         INTEGER                  NOT NULL DEFAULT 5,
    max_attempts                  INTEGER                  NOT NULL DEFAULT 3,
    xp_before                     BIGINT                   NOT NULL DEFAULT 0,
    level_before                  INT                      NOT NULL DEFAULT 1,
    last_activity_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    terms_version_accepted        VARCHAR(32),
    privacy_version_accepted      VARCHAR(32),
    legal_accepted_at             TIMESTAMP WITH TIME ZONE,
    streak_bonus_percent          DOUBLE PRECISION         NOT NULL DEFAULT 0,
    streak_bonus_xp               INTEGER                  NOT NULL DEFAULT 0,
    profile_projection_applied_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_session_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_session_has_user CHECK (user_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_game_sessions_user ON game_sessions (user_id) WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_game_sessions_guest ON game_sessions (guest_token) WHERE guest_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_game_sessions_challenge ON game_sessions (challenge_id);

CREATE INDEX IF NOT EXISTS idx_game_sessions_status ON game_sessions (status);

CREATE INDEX IF NOT EXISTS idx_game_sessions_started ON game_sessions (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_game_sessions_last_activity
    ON game_sessions (last_activity_at DESC);

CREATE INDEX IF NOT EXISTS idx_game_sessions_projection_pending
    ON game_sessions (id)
    WHERE status = 'COMPLETED' AND profile_projection_applied_at IS NULL;

CREATE TABLE IF NOT EXISTS game_session_countries (
    id             BIGINT  PRIMARY KEY,
    session_id     BIGINT  NOT NULL REFERENCES game_sessions (id) ON DELETE CASCADE,
    country_id     BIGINT  NOT NULL REFERENCES cl_schema.countries (id),
    country_order  INTEGER NOT NULL,
    hints_revealed INTEGER NOT NULL DEFAULT 0,
    attempts_used  INTEGER NOT NULL DEFAULT 0,
    is_guessed     BOOLEAN NOT NULL DEFAULT FALSE,
    guessed_at     TIMESTAMP WITH TIME ZONE,
    time_spent_ms  BIGINT  NOT NULL DEFAULT 0,
    xp_earned      INTEGER NOT NULL DEFAULT 0,
    total_hints    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_session_country_order UNIQUE (session_id, country_order)
);

CREATE INDEX IF NOT EXISTS idx_session_countries_session ON game_session_countries (session_id);

CREATE INDEX IF NOT EXISTS idx_session_countries_country ON game_session_countries (country_id);

CREATE TABLE IF NOT EXISTS guess_attempts (
    id                 BIGINT                   PRIMARY KEY,
    session_country_id BIGINT                   NOT NULL REFERENCES game_session_countries (id) ON DELETE CASCADE,
    guessed_country_id BIGINT                   NOT NULL REFERENCES cl_schema.countries (id),
    is_correct         BOOLEAN                  NOT NULL,
    attempt_number     INTEGER                  NOT NULL,
    hints_at_guess     INTEGER                  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_guess_attempts_session_country ON guess_attempts (session_country_id);

CREATE INDEX IF NOT EXISTS idx_guess_attempts_guessed ON guess_attempts (guessed_country_id);

CREATE TABLE IF NOT EXISTS hint_reports (
    id          BIGINT                   PRIMARY KEY,
    hint_id     BIGINT                   NOT NULL,
    user_id     BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    report_type VARCHAR(30)              NOT NULL,
    description TEXT,
    status      VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    admin_notes TEXT,
    resolved_by BIGINT                   REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_report_type CHECK (report_type IN
        ('INCORRECT', 'MISLEADING', 'TOO_EASY', 'TOO_HARD', 'OFFENSIVE', 'OTHER')),
    CONSTRAINT chk_report_status CHECK (status IN ('PENDING', 'REVIEWED', 'FIXED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_hint_reports_hint ON hint_reports (hint_id);

CREATE INDEX IF NOT EXISTS idx_hint_reports_user ON hint_reports (user_id);

CREATE INDEX IF NOT EXISTS idx_hint_reports_status ON hint_reports (status);

CREATE INDEX IF NOT EXISTS idx_hint_reports_created ON hint_reports (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_hint_reports_resolved_by ON hint_reports (resolved_by) WHERE resolved_by IS NOT NULL;

CREATE TABLE IF NOT EXISTS user_daily_participation (
    id              BIGINT                   PRIMARY KEY,
    user_id         BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    challenge_date  DATE                     NOT NULL,
    challenge_level VARCHAR(10)              NOT NULL,
    session_id      BIGINT                   REFERENCES game_sessions (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_daily UNIQUE (user_id, challenge_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_participation_user ON user_daily_participation (user_id);

CREATE INDEX IF NOT EXISTS idx_daily_participation_date ON user_daily_participation (challenge_date DESC);

CREATE TABLE IF NOT EXISTS game_statistics (
    id                       BIGINT                   PRIMARY KEY,
    date                     DATE                     NOT NULL,
    challenge_level          VARCHAR(10)              NOT NULL,
    total_sessions           INTEGER                  NOT NULL DEFAULT 0,
    completed_sessions       INTEGER                  NOT NULL DEFAULT 0,
    abandoned_sessions       INTEGER                  NOT NULL DEFAULT 0,
    total_guesses            INTEGER                  NOT NULL DEFAULT 0,
    correct_guesses          INTEGER                  NOT NULL DEFAULT 0,
    avg_attempts_per_country NUMERIC(5, 2),
    avg_hints_per_country    NUMERIC(5, 2),
    avg_time_per_country_ms  BIGINT,
    unique_users             INTEGER                  NOT NULL DEFAULT 0,
    guest_users              INTEGER                  NOT NULL DEFAULT 0,
    registered_users         INTEGER                  NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_game_stats_date_level UNIQUE (date, challenge_level)
);

CREATE INDEX IF NOT EXISTS idx_game_statistics_date ON game_statistics (date DESC);

CREATE TABLE IF NOT EXISTS country_guess_statistics (
    id                    BIGINT                   PRIMARY KEY,
    country_id            BIGINT                   NOT NULL REFERENCES cl_schema.countries (id) ON DELETE CASCADE,
    date                  DATE                     NOT NULL,
    times_featured        INTEGER                  NOT NULL DEFAULT 0,
    times_guessed_correct INTEGER                  NOT NULL DEFAULT 0,
    times_guessed_wrong   INTEGER                  NOT NULL DEFAULT 0,
    avg_attempts_needed   NUMERIC(5, 2),
    avg_hints_needed      NUMERIC(5, 2),
    avg_time_to_guess_ms  BIGINT,
    confused_with_json    JSONB,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_country_guess_stats UNIQUE (country_id, date)
);

CREATE INDEX IF NOT EXISTS idx_country_guess_stats_country ON country_guess_statistics (country_id);

CREATE INDEX IF NOT EXISTS idx_country_guess_stats_date ON country_guess_statistics (date DESC);

CREATE TABLE IF NOT EXISTS hint_effectiveness (
    id                        BIGINT                   PRIMARY KEY,
    hint_id                   BIGINT                   NOT NULL REFERENCES cl_schema.hints (id) ON DELETE CASCADE,
    date                      DATE                     NOT NULL,
    times_shown               INTEGER                  NOT NULL DEFAULT 0,
    times_led_to_correct      INTEGER                  NOT NULL DEFAULT 0,
    times_led_to_wrong        INTEGER                  NOT NULL DEFAULT 0,
    avg_position_when_correct NUMERIC(5, 2),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hint_effectiveness UNIQUE (hint_id, date)
);

CREATE INDEX IF NOT EXISTS idx_hint_effectiveness_hint ON hint_effectiveness (hint_id);

CREATE INDEX IF NOT EXISTS idx_hint_effectiveness_date ON hint_effectiveness (date DESC);

CREATE TABLE IF NOT EXISTS category_statistics (
    id                   BIGINT                   PRIMARY KEY,
    category             VARCHAR(30)              NOT NULL,
    date                 DATE                     NOT NULL,
    hints_shown          INTEGER                  NOT NULL DEFAULT 0,
    correct_after_hint   INTEGER                  NOT NULL DEFAULT 0,
    effectiveness_rate   NUMERIC(5, 2),
    avg_difficulty_shown NUMERIC(5, 2),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_stats UNIQUE (category, date)
);

CREATE INDEX IF NOT EXISTS idx_category_statistics_category ON category_statistics (category);

CREATE INDEX IF NOT EXISTS idx_category_statistics_date ON category_statistics (date DESC);
