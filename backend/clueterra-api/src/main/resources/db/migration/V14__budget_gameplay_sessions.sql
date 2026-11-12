CREATE TABLE IF NOT EXISTS cl_api_schema.budget_game_session (
    id                            BIGINT                   PRIMARY KEY,
    user_id                       BIGINT                   NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    challenge_id                  BIGINT                   NOT NULL REFERENCES cl_budget_schema.daily_challenge (id) ON DELETE CASCADE,
    status                        VARCHAR(20)              NOT NULL DEFAULT 'IN_PROGRESS',
    current_position              SMALLINT                 NOT NULL DEFAULT 1,
    total_xp_earned               INTEGER                  NOT NULL DEFAULT 0,
    base_xp_per_question          INTEGER                  NOT NULL,
    full_xp_threshold_percent     NUMERIC(5, 2)            NOT NULL,
    xp_curve_power                NUMERIC(6, 3)            NOT NULL,
    level_divisor                 INTEGER                  NOT NULL,
    xp_before                     BIGINT                   NOT NULL,
    level_before                  INTEGER                  NOT NULL,
    started_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at                  TIMESTAMP WITH TIME ZONE,
    xp_applied_at                 TIMESTAMP WITH TIME ZONE,
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    profile_projection_applied_at TIMESTAMP WITH TIME ZONE,
    streak_bonus_percent          DOUBLE PRECISION         NOT NULL DEFAULT 0,
    streak_bonus_xp               INTEGER                  NOT NULL DEFAULT 0,
    CONSTRAINT uq_budget_game_session_user_challenge UNIQUE (user_id, challenge_id),
    CONSTRAINT chk_budget_game_session_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_budget_game_session_position CHECK (current_position BETWEEN 1 AND 6),
    CONSTRAINT chk_budget_game_session_xp CHECK (
        total_xp_earned >= 0
        AND base_xp_per_question BETWEEN 1 AND 10000
        AND full_xp_threshold_percent BETWEEN 0 AND 99
        AND xp_curve_power BETWEEN 0.1 AND 3
        AND level_divisor > 0
        AND xp_before >= 0
        AND level_before > 0
    ),
    CONSTRAINT chk_budget_game_session_lifecycle CHECK (
        (status = 'IN_PROGRESS' AND current_position BETWEEN 1 AND 5 AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND current_position = 6 AND completed_at IS NOT NULL)
        OR (status = 'ABANDONED' AND completed_at IS NULL)
    ),
    CONSTRAINT chk_budget_game_session_xp_applied CHECK (
        xp_applied_at IS NULL OR status = 'COMPLETED'
    ),
    CONSTRAINT chk_budget_game_session_streak_bonus CHECK (
        streak_bonus_percent >= 0 AND streak_bonus_xp >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_budget_game_session_user_started
ON cl_api_schema.budget_game_session (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_budget_game_session_in_progress
ON cl_api_schema.budget_game_session (challenge_id, current_position)
WHERE status = 'IN_PROGRESS';

CREATE INDEX idx_budget_game_session_projection_pending
ON cl_api_schema.budget_game_session (id)
WHERE status = 'COMPLETED' AND profile_projection_applied_at IS NULL;

CREATE TABLE IF NOT EXISTS cl_api_schema.budget_game_answer (
    id                            BIGINT                   PRIMARY KEY,
    session_id                    BIGINT                   NOT NULL REFERENCES cl_api_schema.budget_game_session (id) ON DELETE CASCADE,
    position                      SMALLINT                 NOT NULL,
    question_variant_id           BIGINT                   NOT NULL REFERENCES cl_budget_schema.question_variant (id),
    question_type                 VARCHAR(30)              NOT NULL,
    guessed_value                 NUMERIC(24, 6),
    guessed_choice                VARCHAR(20),
    answer_value                  NUMERIC(24, 6),
    answer_unit                   VARCHAR(40),
    answer_choice                 VARCHAR(20),
    accuracy_percent              NUMERIC(5, 2)            NOT NULL,
    xp_percent                    NUMERIC(5, 2)            NOT NULL,
    xp_earned                     INTEGER                  NOT NULL,
    reveal_payload                JSONB                    NOT NULL,
    answered_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    profile_projection_applied_at TIMESTAMP WITH TIME ZONE,
    raw_xp_percent                NUMERIC(5, 2)            NOT NULL,
    raw_xp_earned                 INTEGER                  NOT NULL,
    hint_multiplier_percent       NUMERIC(5, 2)            NOT NULL DEFAULT 100,
    hints_used                    SMALLINT                 NOT NULL DEFAULT 0,
    CONSTRAINT uq_budget_game_answer_position UNIQUE (session_id, position),
    CONSTRAINT uq_budget_game_answer_variant UNIQUE (session_id, question_variant_id),
    CONSTRAINT chk_budget_game_answer_position CHECK (position BETWEEN 1 AND 5),
    CONSTRAINT chk_budget_game_answer_score CHECK (
        accuracy_percent BETWEEN 0 AND 100
        AND xp_percent BETWEEN 0 AND 100
        AND xp_earned >= 0
    ),
    CONSTRAINT chk_budget_game_answer_reveal CHECK (JSONB_TYPEOF(reveal_payload) = 'object'),
    CONSTRAINT chk_budget_game_answer_type CHECK (question_type IN (
        'HOW_MUCH',
        'CURRENT_ESTIMATE',
        'SPENT_SO_FAR',
        'ORIGINAL_BUDGET',
        'BUDGET_OVERRUN',
        'HOW_LATE',
        'HIGHER_LOWER',
        'COST_PER_UNIT'
    )),
    CONSTRAINT chk_budget_game_answer_hint_score CHECK (
        raw_xp_percent BETWEEN 0 AND 100
        AND raw_xp_earned >= xp_earned
        AND hint_multiplier_percent > 0
        AND hint_multiplier_percent <= 100
        AND hints_used BETWEEN 0 AND 5
    ),
    CONSTRAINT chk_budget_game_answer_shape CHECK (
        (
            question_type = 'HIGHER_LOWER'
            AND guessed_value IS NULL
            AND guessed_choice IN ('PRIMARY', 'COMPARISON')
            AND answer_value IS NULL
            AND answer_unit IS NULL
            AND answer_choice IN ('PRIMARY', 'COMPARISON')
    )
        OR (
            question_type IN ('BUDGET_OVERRUN', 'HOW_LATE')
            AND guessed_value >= 0
            AND guessed_choice IS NULL
            AND answer_value >= 0
            AND BTRIM(answer_unit) <> ''
            AND answer_choice IS NULL
    )
        OR (
            question_type NOT IN ('HIGHER_LOWER', 'BUDGET_OVERRUN', 'HOW_LATE')
            AND guessed_value > 0
            AND guessed_choice IS NULL
            AND answer_value > 0
            AND BTRIM(answer_unit) <> ''
            AND answer_choice IS NULL
    )
    )
);

CREATE INDEX IF NOT EXISTS idx_budget_game_answer_session
ON cl_api_schema.budget_game_answer (session_id, position);

CREATE INDEX idx_budget_game_answer_projection_pending
ON cl_api_schema.budget_game_answer (id)
WHERE profile_projection_applied_at IS NULL;

INSERT INTO cl_api_schema.app_settings (key, value)
VALUES
    ('budget_base_xp_per_question', '100'),
    ('budget_full_xp_threshold_percent', '20'),
    ('budget_xp_curve_power', '0.75')
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE FUNCTION cl_api_schema.start_budget_game_session (
    p_session_id BIGINT,
    p_user_id BIGINT,
    p_base_xp_per_question INTEGER,
    p_full_xp_threshold_percent NUMERIC,
    p_xp_curve_power NUMERIC,
    p_level_divisor INTEGER
)
RETURNS TABLE (
    outcome TEXT,
    session_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_challenge_id BIGINT;
    v_question_count INTEGER;
    v_session_id BIGINT;
    v_xp_before BIGINT;
    v_level_before INTEGER;
BEGIN
    IF p_session_id IS NULL OR p_user_id IS NULL THEN
        RETURN QUERY SELECT 'INVALID_REQUEST'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    IF p_base_xp_per_question NOT BETWEEN 1 AND 10000
        OR p_full_xp_threshold_percent NOT BETWEEN 0 AND 99
        OR p_xp_curve_power NOT BETWEEN 0.1 AND 3
        OR p_level_divisor <= 0 THEN
        RETURN QUERY SELECT 'INVALID_SETTINGS'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    PERFORM 1
    FROM cl_api_schema.users app_user
    WHERE app_user.id = p_user_id
        AND app_user.enabled
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT 'USER_NOT_FOUND'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    SELECT
        challenge.id,
        COUNT(question.position)::INTEGER
    INTO
        v_challenge_id,
        v_question_count
    FROM cl_budget_schema.daily_challenge challenge
    LEFT JOIN cl_budget_schema.daily_challenge_question question
        ON question.challenge_id = challenge.id
    WHERE challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
        AND challenge.status = 'PUBLISHED'
    GROUP BY challenge.id;

    IF v_challenge_id IS NULL THEN
        RETURN QUERY SELECT 'CHALLENGE_NOT_FOUND'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    IF v_question_count <> 5 THEN
        RETURN QUERY SELECT 'INVALID_CHALLENGE'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    PERFORM PG_ADVISORY_XACT_LOCK(
        HASHTEXTEXTENDED('budget-game-session:' || p_user_id || ':' || v_challenge_id, 0)
    );

    SELECT profile.total_xp,
           profile.current_level
    INTO
        v_xp_before,
        v_level_before
    FROM cl_api_schema.user_profiles profile
    WHERE profile.user_id = p_user_id;

    v_xp_before := COALESCE(v_xp_before, 0);
    v_level_before := COALESCE(v_level_before, 1);

    INSERT INTO cl_api_schema.budget_game_session (
        id,
        user_id,
        challenge_id,
        base_xp_per_question,
        full_xp_threshold_percent,
        xp_curve_power,
        level_divisor,
        xp_before,
        level_before
    )
    VALUES (
        p_session_id,
        p_user_id,
        v_challenge_id,
        p_base_xp_per_question,
        p_full_xp_threshold_percent,
        p_xp_curve_power,
        p_level_divisor,
        v_xp_before,
        v_level_before
    )
    ON CONFLICT (user_id, challenge_id) DO NOTHING
    RETURNING id INTO v_session_id;

    IF v_session_id IS NOT NULL THEN
        RETURN QUERY SELECT 'STARTED'::TEXT, v_session_id;
        RETURN;
    END IF;

    SELECT session.id
    INTO v_session_id
    FROM cl_api_schema.budget_game_session session
    WHERE session.user_id = p_user_id
        AND session.challenge_id = v_challenge_id;

    RETURN QUERY SELECT 'EXISTING'::TEXT, v_session_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.abandon_expired_budget_sessions (
    p_today DATE
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_updated INTEGER;
BEGIN
    UPDATE cl_api_schema.budget_game_session session
    SET status = 'ABANDONED', updated_at = NOW()
    FROM cl_budget_schema.daily_challenge challenge
    WHERE challenge.id = session.challenge_id
        AND challenge.challenge_date < p_today
        AND session.status = 'IN_PROGRESS';

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated;
END;
$$;
