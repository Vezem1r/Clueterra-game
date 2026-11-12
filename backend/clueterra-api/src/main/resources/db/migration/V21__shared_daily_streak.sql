CREATE TABLE cl_api_schema.daily_streak_game (
    code       VARCHAR(40)              PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO cl_api_schema.daily_streak_game (code)
VALUES ('CLUETERRA'), ('BUDGET')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE cl_api_schema.user_daily_activity (
    user_id       BIGINT                   NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    activity_date DATE                     NOT NULL,
    game_code     VARCHAR(40)              NOT NULL REFERENCES cl_api_schema.daily_streak_game (code),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, activity_date, game_code)
);

CREATE INDEX idx_user_daily_activity_date
ON cl_api_schema.user_daily_activity (activity_date, user_id);

CREATE OR REPLACE FUNCTION cl_api_schema.record_daily_streak_activity (
    p_user_id BIGINT,
    p_game_code VARCHAR,
    p_max_bonus INTEGER,
    p_max_days INTEGER,
    p_curve_power DOUBLE PRECISION,
    p_freeze_interval INTEGER,
    p_freeze_max INTEGER
)
RETURNS TABLE (
    new_streak INTEGER,
    new_best_streak INTEGER,
    streak_bonus_percent DOUBLE PRECISION,
    freezes_earned INTEGER,
    new_freezes INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_utc_today DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE;
    v_profile cl_api_schema.user_profiles%ROWTYPE;
    v_counted_today BOOLEAN;
    v_continues BOOLEAN;
    v_new_streak INTEGER;
    v_new_best INTEGER;
    v_freezes_earned INTEGER := 0;
    v_new_freezes INTEGER;
BEGIN
    INSERT INTO cl_api_schema.user_profiles (user_id)
    VALUES (p_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT *
    INTO v_profile
    FROM cl_api_schema.user_profiles profile
    WHERE profile.user_id = p_user_id
    FOR UPDATE;

    SELECT EXISTS (
        SELECT 1
        FROM cl_api_schema.user_daily_activity activity
        WHERE activity.user_id = p_user_id
            AND activity.activity_date = v_utc_today
    )
    INTO v_counted_today;

    INSERT INTO cl_api_schema.user_daily_activity (user_id, activity_date, game_code)
    VALUES (p_user_id, v_utc_today, p_game_code)
    ON CONFLICT DO NOTHING;

    IF v_counted_today THEN
        v_new_streak := v_profile.current_streak;
        v_new_best := v_profile.best_streak;
        v_new_freezes := v_profile.streak_freezes;
    ELSE
        v_continues := EXISTS (
            SELECT 1
            FROM cl_api_schema.user_daily_activity activity
            WHERE activity.user_id = p_user_id
                AND activity.activity_date = v_utc_today - 1
        ) OR (
            v_profile.streak_freeze_used_at IS NOT NULL
            AND (v_profile.streak_freeze_used_at AT TIME ZONE 'UTC')::DATE = v_utc_today
        );

        v_new_streak := CASE WHEN v_continues THEN v_profile.current_streak + 1 ELSE 1 END;
        v_new_best := GREATEST(v_profile.best_streak, v_new_streak);

        IF p_freeze_interval > 0
            AND v_new_streak > v_profile.current_streak
            AND v_new_streak % p_freeze_interval = 0 THEN
            v_freezes_earned := 1;
        END IF;
        v_new_freezes := LEAST(v_profile.streak_freezes + v_freezes_earned, GREATEST(0, p_freeze_max));

        UPDATE cl_api_schema.user_profiles profile
        SET
            current_streak = v_new_streak,
            best_streak = v_new_best,
            streak_freezes = v_new_freezes,
            streak_freezes_used_since_completion = 0,
            streak_freezes_acknowledged_since_completion = 0,
            updated_at = NOW()
        WHERE profile.user_id = p_user_id;
    END IF;

    RETURN QUERY SELECT
        v_new_streak,
        v_new_best,
        cl_api_schema.calc_streak_bonus(v_new_streak, p_max_bonus, p_max_days, p_curve_power),
        v_freezes_earned,
        v_new_freezes;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.award_session_xp (
    p_user_id BIGINT,
    p_base_xp INTEGER,
    p_won BOOLEAN,
    p_level_divisor INTEGER,
    p_max_bonus INTEGER,
    p_max_days INTEGER,
    p_curve_power DOUBLE PRECISION,
    p_freeze_interval INTEGER,
    p_freeze_max INTEGER
)
RETURNS TABLE (
    new_streak INTEGER,
    new_best_streak INTEGER,
    streak_bonus_percent DOUBLE PRECISION,
    streak_bonus_xp INTEGER,
    total_xp_awarded INTEGER,
    freezes_earned INTEGER,
    new_level INTEGER,
    new_freezes INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_streak RECORD;
    v_bonus_xp INTEGER;
    v_total_xp INTEGER;
    v_new_level INTEGER;
BEGIN
    SELECT *
    INTO v_streak
    FROM cl_api_schema.record_daily_streak_activity(
        p_user_id, 'CLUETERRA', p_max_bonus, p_max_days, p_curve_power, p_freeze_interval, p_freeze_max);

    v_bonus_xp := FLOOR(p_base_xp * v_streak.streak_bonus_percent / 100.0)::INTEGER;
    v_total_xp := p_base_xp + v_bonus_xp;

    UPDATE cl_api_schema.user_profiles profile
    SET
        total_xp = profile.total_xp + v_total_xp,
        current_level = GREATEST(
            profile.current_level,
            FLOOR(
                SQRT(
                    (profile.total_xp + v_total_xp)::DOUBLE PRECISION
                    / GREATEST(1, p_level_divisor)::DOUBLE PRECISION
                )
            )::INTEGER + 1
        ),
        challenges_played = profile.challenges_played + 1,
        challenges_won = CASE WHEN p_won THEN profile.challenges_won + 1 ELSE profile.challenges_won END,
        updated_at = NOW()
    WHERE profile.user_id = p_user_id
    RETURNING profile.current_level INTO v_new_level;

    RETURN QUERY SELECT
        v_streak.new_streak,
        v_streak.new_best_streak,
        v_streak.streak_bonus_percent,
        v_bonus_xp,
        v_total_xp,
        v_streak.freezes_earned,
        v_new_level,
        v_streak.new_freezes;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.apply_budget_session_xp (
    p_session_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id BIGINT;
    v_total_xp INTEGER;
    v_level_divisor INTEGER;
    v_challenge_date DATE;
    v_bonus_percent DOUBLE PRECISION := 0;
    v_bonus_xp INTEGER := 0;
BEGIN
    SELECT
        session.user_id,
        session.total_xp_earned,
        session.level_divisor,
        challenge.challenge_date
    INTO
        v_user_id,
        v_total_xp,
        v_level_divisor,
        v_challenge_date
    FROM cl_api_schema.budget_game_session session
    JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = session.challenge_id
    WHERE session.id = p_session_id
        AND session.status = 'COMPLETED'
        AND session.xp_applied_at IS NULL
    FOR UPDATE OF session;

    IF v_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    PERFORM 1
    FROM cl_api_schema.users app_user
    WHERE app_user.id = v_user_id
        AND app_user.enabled
        AND NOT app_user.is_guest
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    IF v_challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE THEN
        SELECT streak.streak_bonus_percent
        INTO v_bonus_percent
        FROM cl_api_schema.record_daily_streak_activity(
            v_user_id,
            'BUDGET',
            COALESCE((SELECT value::INTEGER FROM cl_api_schema.app_settings WHERE key = 'streak_max_bonus_percent'), 50),
            COALESCE((SELECT value::INTEGER FROM cl_api_schema.app_settings WHERE key = 'streak_max_days'), 365),
            COALESCE((SELECT value::DOUBLE PRECISION FROM cl_api_schema.app_settings WHERE key = 'streak_curve_power'), 0.4),
            COALESCE((SELECT value::INTEGER FROM cl_api_schema.app_settings WHERE key = 'streak_freeze_streak_interval'), 10),
            COALESCE((SELECT value::INTEGER FROM cl_api_schema.app_settings WHERE key = 'streak_freeze_max'), 5)) streak;
        v_bonus_xp := FLOOR(v_total_xp * v_bonus_percent / 100.0)::INTEGER;
    END IF;

    UPDATE cl_api_schema.user_profiles profile
    SET
        total_xp = profile.total_xp + v_total_xp + v_bonus_xp,
        current_level = GREATEST(
            profile.current_level,
            FLOOR(
                SQRT(
                    (profile.total_xp + v_total_xp + v_bonus_xp)::DOUBLE PRECISION
                    / GREATEST(v_level_divisor, 1)::DOUBLE PRECISION
                )
            )::INTEGER + 1
        ),
        updated_at = NOW()
    WHERE profile.user_id = v_user_id;

    UPDATE cl_api_schema.budget_game_session session
    SET
        streak_bonus_percent = v_bonus_percent,
        streak_bonus_xp = v_bonus_xp,
        xp_applied_at = NOW(),
        updated_at = NOW()
    WHERE session.id = p_session_id
        AND session.xp_applied_at IS NULL;

    RETURN FOUND;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.reset_expired_streaks ()
RETURNS TABLE (freezes_consumed INTEGER, streaks_reset INTEGER)
LANGUAGE plpgsql
AS $$
DECLARE
    v_utc_today DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE;
    v_freezes_consumed INTEGER;
    v_streaks_reset INTEGER;
BEGIN
    WITH shielded AS (
        UPDATE cl_api_schema.user_profiles profile
        SET
            streak_freezes = profile.streak_freezes - 1,
            streak_freeze_used_at = NOW(),
            streak_freezes_used_since_completion = profile.streak_freezes_used_since_completion + 1,
            updated_at = NOW()
        WHERE profile.current_streak > 0
            AND profile.streak_freezes > 0
            AND (
                profile.streak_freeze_used_at IS NULL
                OR (profile.streak_freeze_used_at AT TIME ZONE 'UTC')::DATE < v_utc_today
            )
            AND NOT EXISTS (
                SELECT 1
                FROM cl_api_schema.user_daily_activity activity
                WHERE activity.user_id = profile.user_id
                    AND activity.activity_date >= v_utc_today - 1
            )
        RETURNING profile.user_id
    )
    SELECT COUNT(*)::INTEGER INTO v_freezes_consumed FROM shielded;

    UPDATE cl_api_schema.user_profiles profile
    SET
        current_streak = 0,
        streak_freezes_used_since_completion = 0,
        streak_freezes_acknowledged_since_completion = 0,
        updated_at = NOW()
    WHERE profile.current_streak > 0
        AND profile.streak_freezes = 0
        AND (
            profile.streak_freeze_used_at IS NULL
            OR (profile.streak_freeze_used_at AT TIME ZONE 'UTC')::DATE < v_utc_today
        )
        AND NOT EXISTS (
            SELECT 1
            FROM cl_api_schema.user_daily_activity activity
            WHERE activity.user_id = profile.user_id
                AND activity.activity_date >= v_utc_today - 1
        );
    GET DIAGNOSTICS v_streaks_reset = ROW_COUNT;

    RETURN QUERY SELECT v_freezes_consumed, v_streaks_reset;
END;
$$;
