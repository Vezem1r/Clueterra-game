CREATE TABLE IF NOT EXISTS user_country_solutions (
    user_id           BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    country_id        BIGINT                   NOT NULL REFERENCES cl_schema.countries (id) ON DELETE CASCADE,
    first_solved_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    source_session_id BIGINT                   REFERENCES game_sessions (id) ON DELETE SET NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, country_id)
);

CREATE INDEX IF NOT EXISTS idx_user_country_solutions_country
    ON user_country_solutions (country_id);

CREATE INDEX IF NOT EXISTS idx_user_country_solutions_session
    ON user_country_solutions (source_session_id)
    WHERE source_session_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS user_difficulty_stats (
    user_id           BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    challenge_level   VARCHAR(10)              NOT NULL,
    completed_runs    INTEGER                  NOT NULL DEFAULT 0,
    countries_played  INTEGER                  NOT NULL DEFAULT 0,
    countries_solved  INTEGER                  NOT NULL DEFAULT 0,
    last_completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, challenge_level)
);

CREATE INDEX IF NOT EXISTS idx_user_difficulty_stats_favorite
    ON user_difficulty_stats (user_id, completed_runs DESC, last_completed_at DESC, challenge_level);

CREATE OR REPLACE FUNCTION apply_completed_session_projection(p_session_id BIGINT)
    RETURNS BOOLEAN AS
$$
DECLARE
    v_user_id BIGINT;
    v_challenge_level VARCHAR(10);
    v_completed_at TIMESTAMP WITH TIME ZONE;
    v_projection_applied_at TIMESTAMP WITH TIME ZONE;
    v_countries_played INTEGER := 0;
    v_countries_solved INTEGER := 0;
    v_perfect_run BOOLEAN := FALSE;
    v_favorite_difficulty VARCHAR(10);
BEGIN
    SELECT user_id,
           challenge_level,
           COALESCE(completed_at, NOW()),
           profile_projection_applied_at
    INTO v_user_id,
         v_challenge_level,
         v_completed_at,
         v_projection_applied_at
    FROM game_sessions
    WHERE id = p_session_id
      AND status = 'COMPLETED'
    FOR UPDATE;

    IF v_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    IF v_projection_applied_at IS NOT NULL THEN
        RETURN FALSE;
    END IF;

    SELECT COUNT(*)::INTEGER,
           COUNT(*) FILTER (WHERE is_guessed)::INTEGER,
           COUNT(*) > 0
               AND COUNT(*) FILTER (WHERE is_guessed AND attempts_used = 1) = COUNT(*) AS perfect_run
    INTO v_countries_played,
         v_countries_solved,
         v_perfect_run
    FROM game_session_countries
    WHERE session_id = p_session_id;

    INSERT INTO user_profiles (user_id, total_xp, current_level, challenges_played,
                               challenges_won, current_streak, best_streak, created_at, updated_at)
    VALUES (v_user_id, 0, 1, 0, 0, 0, 0, NOW(), NOW())
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO user_country_solutions (user_id, country_id, first_solved_at, source_session_id)
    SELECT v_user_id,
           gsc.country_id,
           COALESCE(gsc.guessed_at, v_completed_at, NOW()),
           p_session_id
    FROM game_session_countries gsc
    WHERE gsc.session_id = p_session_id
      AND gsc.is_guessed = TRUE
    ON CONFLICT (user_id, country_id) DO NOTHING;

    IF v_challenge_level IS NOT NULL THEN
        INSERT INTO user_difficulty_stats (user_id, challenge_level, completed_runs,
                                           countries_played, countries_solved, last_completed_at)
        VALUES (v_user_id, v_challenge_level, 1,
                COALESCE(v_countries_played, 0), COALESCE(v_countries_solved, 0), v_completed_at)
        ON CONFLICT (user_id, challenge_level) DO UPDATE SET
            completed_runs = user_difficulty_stats.completed_runs + 1,
            countries_played = user_difficulty_stats.countries_played + EXCLUDED.countries_played,
            countries_solved = user_difficulty_stats.countries_solved + EXCLUDED.countries_solved,
            last_completed_at = GREATEST(user_difficulty_stats.last_completed_at, EXCLUDED.last_completed_at);
    END IF;

    SELECT challenge_level
    INTO v_favorite_difficulty
    FROM user_difficulty_stats
    WHERE user_id = v_user_id
    ORDER BY completed_runs DESC, last_completed_at DESC NULLS LAST, challenge_level
    LIMIT 1;

    UPDATE user_profiles
    SET countries_played    = countries_played + COALESCE(v_countries_played, 0),
        countries_solved    = countries_solved + COALESCE(v_countries_solved, 0),
        perfect_runs        = perfect_runs + CASE WHEN COALESCE(v_perfect_run, FALSE) THEN 1 ELSE 0 END,
        favorite_difficulty = v_favorite_difficulty,
        last_completed_at   = GREATEST(COALESCE(last_completed_at, v_completed_at), v_completed_at),
        updated_at          = NOW()
    WHERE user_id = v_user_id;

    UPDATE game_sessions
    SET profile_projection_applied_at = NOW()
    WHERE id = p_session_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION claim_guest_today_progress(
    p_guest_token TEXT,
    p_target_user_id BIGINT
) RETURNS TABLE(
    claim_status TEXT,
    guest_user_id BIGINT,
    target_user_id BIGINT,
    session_id BIGINT,
    session_status TEXT
) AS $$
DECLARE
    v_today DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE;
    v_guest_user_id BIGINT;
    v_session_id BIGINT;
    v_session_status TEXT;
    v_base_xp INTEGER;
    v_won BOOLEAN;
    v_xp_before BIGINT := 0;
    v_level_before INTEGER := 1;
    v_award RECORD;
    v_rows INTEGER;
    v_level_divisor INTEGER;
    v_streak_max_bonus_percent INTEGER;
    v_streak_max_days INTEGER;
    v_streak_curve_power DOUBLE PRECISION;
    v_streak_freeze_interval INTEGER;
    v_streak_freeze_max INTEGER;
BEGIN
    IF p_guest_token IS NULL OR BTRIM(p_guest_token) = '' OR p_target_user_id IS NULL THEN
        RETURN QUERY SELECT 'NO_GUEST'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    PERFORM 1
    FROM users
    WHERE id = p_target_user_id
      AND is_guest = FALSE
      AND enabled = TRUE
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT 'TARGET_NOT_FOUND'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    SELECT id
    INTO v_guest_user_id
    FROM users
    WHERE guest_token = p_guest_token
      AND is_guest = TRUE
      AND guest_token_expires_at > NOW()
    FOR UPDATE;

    IF v_guest_user_id IS NULL THEN
        RETURN QUERY SELECT 'NO_GUEST'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    PERFORM 1
    FROM user_daily_participation
    WHERE user_id = p_target_user_id
      AND challenge_date = v_today
    FOR UPDATE;

    IF FOUND THEN
        RETURN QUERY SELECT 'TARGET_ALREADY_PLAYED'::TEXT, v_guest_user_id, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    SELECT udp.session_id, gs.status
    INTO v_session_id, v_session_status
    FROM user_daily_participation udp
    JOIN game_sessions gs ON gs.id = udp.session_id
    WHERE udp.user_id = v_guest_user_id
      AND udp.challenge_date = v_today
    FOR UPDATE OF udp, gs;

    IF v_session_id IS NULL THEN
        RETURN QUERY SELECT 'NO_TODAY_PROGRESS'::TEXT, v_guest_user_id, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    SELECT up.total_xp, up.current_level
    INTO v_xp_before, v_level_before
    FROM user_profiles up
    WHERE up.user_id = p_target_user_id;

    v_xp_before := COALESCE(v_xp_before, 0);
    v_level_before := COALESCE(v_level_before, 1);

    BEGIN
        UPDATE user_daily_participation udp
        SET user_id = p_target_user_id
        WHERE udp.user_id = v_guest_user_id
          AND udp.challenge_date = v_today
          AND udp.session_id = v_session_id
          AND NOT EXISTS (
              SELECT 1
              FROM user_daily_participation target_udp
              WHERE target_udp.user_id = p_target_user_id
                AND target_udp.challenge_date = v_today
          );
        GET DIAGNOSTICS v_rows = ROW_COUNT;
    EXCEPTION WHEN unique_violation THEN
        RETURN QUERY SELECT 'TARGET_ALREADY_PLAYED'::TEXT, v_guest_user_id, p_target_user_id, v_session_id, v_session_status;
        RETURN;
    END;

    IF v_rows = 0 THEN
        RETURN QUERY SELECT 'TARGET_ALREADY_PLAYED'::TEXT, v_guest_user_id, p_target_user_id, v_session_id, v_session_status;
        RETURN;
    END IF;

    DELETE FROM user_country_solutions
    WHERE user_id = v_guest_user_id
      AND source_session_id = v_session_id;

    UPDATE game_sessions
    SET user_id = p_target_user_id,
        guest_token = NULL,
        xp_before = v_xp_before,
        level_before = v_level_before,
        profile_projection_applied_at = NULL
    WHERE id = v_session_id;

    UPDATE legal_acceptance_events
    SET user_id = p_target_user_id
    WHERE user_id = v_guest_user_id
      AND acceptance_source LIKE 'GUEST_%'
      AND DATE(accepted_at AT TIME ZONE 'UTC') = v_today;

    UPDATE users
    SET guest_token = NULL,
        guest_token_expires_at = NOW() - INTERVAL '1 second',
        updated_at = NOW()
    WHERE id = v_guest_user_id;

    IF v_session_status = 'COMPLETED' THEN
        SELECT GREATEST(gs.total_xp_earned - gs.streak_bonus_xp, 0)::INTEGER,
               EXISTS (
                   SELECT 1
                   FROM game_session_countries gsc
                   WHERE gsc.session_id = gs.id
                     AND gsc.is_guessed = TRUE
               )
        INTO v_base_xp, v_won
        FROM game_sessions gs
        WHERE gs.id = v_session_id;

        SELECT COALESCE((SELECT value::INTEGER FROM app_settings WHERE key = 'level_divisor'), 100),
               COALESCE((SELECT value::INTEGER FROM app_settings WHERE key = 'streak_max_bonus_percent'), 50),
               COALESCE((SELECT value::INTEGER FROM app_settings WHERE key = 'streak_max_days'), 365),
               COALESCE((SELECT value::DOUBLE PRECISION FROM app_settings WHERE key = 'streak_curve_power'), 0.4),
               COALESCE((SELECT value::INTEGER FROM app_settings WHERE key = 'streak_freeze_streak_interval'), 10),
               COALESCE((SELECT value::INTEGER FROM app_settings WHERE key = 'streak_freeze_max'), 5)
        INTO v_level_divisor,
             v_streak_max_bonus_percent,
             v_streak_max_days,
             v_streak_curve_power,
             v_streak_freeze_interval,
             v_streak_freeze_max;

        SELECT *
        INTO v_award
        FROM award_session_xp(
            p_target_user_id,
            COALESCE(v_base_xp, 0),
            COALESCE(v_won, FALSE),
            v_level_divisor,
            v_streak_max_bonus_percent,
            v_streak_max_days,
            v_streak_curve_power,
            v_streak_freeze_interval,
            v_streak_freeze_max
        );

        UPDATE game_sessions
        SET total_xp_earned = v_award.total_xp_awarded,
            streak_bonus_percent = v_award.streak_bonus_percent,
            streak_bonus_xp = v_award.streak_bonus_xp
        WHERE id = v_session_id;

        PERFORM apply_completed_session_projection(v_session_id);

        RETURN QUERY SELECT 'CLAIMED_COMPLETED'::TEXT, v_guest_user_id, p_target_user_id, v_session_id, v_session_status;
        RETURN;
    END IF;

    RETURN QUERY SELECT 'CLAIMED_IN_PROGRESS'::TEXT, v_guest_user_id, p_target_user_id, v_session_id, v_session_status;
END;
$$ LANGUAGE plpgsql;
