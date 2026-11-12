CREATE TABLE cl_api_schema.silhouette_game_player_state (
    user_id             BIGINT      PRIMARY KEY REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    current_question_id BIGINT      REFERENCES cl_schema.silhouette_game_question (id),
    current_hints_used  SMALLINT    NOT NULL DEFAULT 0,
    current_attempts    INTEGER     NOT NULL DEFAULT 0,
    round_number        BIGINT      NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_silhouette_game_state_hints CHECK (current_hints_used BETWEEN 0 AND 4),
    CONSTRAINT chk_silhouette_game_state_attempts CHECK (current_attempts >= 0),
    CONSTRAINT chk_silhouette_game_state_round CHECK (round_number >= 0),
    CONSTRAINT chk_silhouette_game_state_empty CHECK (
        current_question_id IS NOT NULL OR (current_hints_used = 0 AND current_attempts = 0)
    )
);

COMMENT ON TABLE cl_api_schema.silhouette_game_player_state IS
    'One durable current silhouette per guest or registered user.';

CREATE TABLE cl_api_schema.silhouette_game_progress (
    user_id              BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    question_id          BIGINT      NOT NULL REFERENCES cl_schema.silhouette_game_question (id),
    first_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    first_attempt_at     TIMESTAMPTZ,
    first_solved_at      TIMESTAMPTZ,
    last_skipped_at      TIMESTAMPTZ,
    last_revealed_at     TIMESTAMPTZ,
    attempt_count        INTEGER     NOT NULL DEFAULT 0,
    skip_count           INTEGER     NOT NULL DEFAULT 0,
    reveal_count         INTEGER     NOT NULL DEFAULT 0,
    hints_revealed       SMALLINT    NOT NULL DEFAULT 0,
    best_hints_used      SMALLINT,
    xp_awarded           INTEGER     NOT NULL DEFAULT 0,
    deferred_until_round BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, question_id),
    CONSTRAINT chk_silhouette_game_progress_counts CHECK (
        attempt_count >= 0 AND skip_count >= 0 AND reveal_count >= 0 AND xp_awarded >= 0
        AND deferred_until_round >= 0
        AND hints_revealed BETWEEN 0 AND 4
        AND (best_hints_used IS NULL OR best_hints_used BETWEEN 0 AND 4)
    ),
    CONSTRAINT chk_silhouette_game_progress_attempt CHECK (
        (attempt_count = 0 AND first_attempt_at IS NULL)
        OR (attempt_count > 0 AND first_attempt_at IS NOT NULL)
    ),
    CONSTRAINT chk_silhouette_game_progress_reveal CHECK (
        (reveal_count = 0 AND last_revealed_at IS NULL)
        OR (reveal_count > 0 AND last_revealed_at IS NOT NULL)
    ),
    CONSTRAINT chk_silhouette_game_progress_solve CHECK (
        (first_solved_at IS NULL AND best_hints_used IS NULL)
        OR (first_solved_at IS NOT NULL AND best_hints_used IS NOT NULL)
    )
);

CREATE INDEX idx_silhouette_game_progress_solved
    ON cl_api_schema.silhouette_game_progress (user_id, first_solved_at);

COMMENT ON TABLE cl_api_schema.silhouette_game_progress IS
    'One row per player and silhouette question: the history of every country a guest or user has seen. Merges use this key to avoid duplicate progress and XP.';

CREATE TABLE cl_api_schema.silhouette_game_attempt (
    user_id      BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    round_number BIGINT      NOT NULL,
    guess_iso2   CHAR(2)     NOT NULL,
    question_id  BIGINT      NOT NULL REFERENCES cl_schema.silhouette_game_question (id),
    correct      BOOLEAN     NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CLOCK_TIMESTAMP(),
    PRIMARY KEY (user_id, round_number, guess_iso2),
    CONSTRAINT chk_silhouette_game_attempt_iso CHECK (guess_iso2 ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_silhouette_game_attempt_round CHECK (round_number > 0)
);

CREATE INDEX idx_silhouette_game_attempt_question
    ON cl_api_schema.silhouette_game_attempt (question_id);

COMMENT ON TABLE cl_api_schema.silhouette_game_attempt IS
    'Every country a player named in a silhouette round, so a resumed round shows its wrong guesses again.';

CREATE VIEW cl_api_schema.user_silhouette_stats AS
SELECT progress.user_id,
       COUNT(*) FILTER (WHERE progress.first_attempt_at IS NOT NULL) AS attempted_countries,
       COUNT(*) FILTER (WHERE progress.first_solved_at IS NOT NULL) AS solved_countries,
       COALESCE(SUM(progress.attempt_count), 0) AS answer_attempts,
       COALESCE(SUM(progress.skip_count), 0) AS skips
FROM cl_api_schema.silhouette_game_progress progress
GROUP BY progress.user_id;

COMMENT ON VIEW cl_api_schema.user_silhouette_stats IS
    'Per-player silhouette counts; a country counts as solved once and stays painted on the player globe.';

INSERT INTO cl_api_schema.app_settings (key, value)
VALUES ('silhouette_game_base_xp', '150'),
       ('silhouette_game_hint_penalty_percent', '8')
ON CONFLICT (key) DO NOTHING;

CREATE SEQUENCE cl_api_schema.silhouette_game_xp_event_id_seq
    START WITH -4000000000000000000 INCREMENT BY -1
    MINVALUE -4999999999999999999 MAXVALUE -4000000000000000000;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_next_question(p_user_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_solved INTEGER;
    v_pattern TEXT;
    v_band INTEGER;
    v_recent_hints NUMERIC;
    v_question_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cl_api_schema.users WHERE id = p_user_id AND enabled) THEN
        RAISE EXCEPTION 'Silhouette game player is unavailable' USING ERRCODE = 'CT409';
    END IF;

    INSERT INTO cl_api_schema.silhouette_game_player_state (user_id)
    VALUES (p_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT * INTO v_state
    FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_state.current_question_id IS NOT NULL THEN
        RETURN v_state.current_question_id;
    END IF;

    SELECT COUNT(*)::INTEGER INTO v_solved
    FROM cl_api_schema.silhouette_game_progress
    WHERE user_id = p_user_id AND first_solved_at IS NOT NULL;

    SELECT AVG(recent.best_hints_used) INTO v_recent_hints
    FROM (
        SELECT best_hints_used
        FROM cl_api_schema.silhouette_game_progress
        WHERE user_id = p_user_id AND first_solved_at IS NOT NULL
        ORDER BY first_solved_at DESC
        LIMIT 5
    ) recent;

    v_pattern := CASE
        WHEN v_solved < 40 THEN 'EEEMMH'
        WHEN v_solved < 100 THEN 'EEMMH'
        WHEN v_solved < 150 THEN 'EMMHH'
        ELSE 'MHH'
    END;
    v_band := POSITION(SUBSTRING(v_pattern FROM (v_state.round_number % LENGTH(v_pattern))::INTEGER + 1 FOR 1) IN 'EMH') - 1;
    IF v_recent_hints >= 2.5 THEN
        v_band := GREATEST(0, v_band - 1);
    END IF;

    SELECT country.id INTO v_question_id
    FROM cl_schema.silhouette_game_question country
    LEFT JOIN cl_api_schema.silhouette_game_progress progress
        ON progress.question_id = country.id AND progress.user_id = p_user_id
    WHERE country.is_active AND progress.first_solved_at IS NULL
    ORDER BY
        CASE WHEN COALESCE(progress.deferred_until_round, 0) > v_state.round_number THEN 1 ELSE 0 END,
        ABS(CASE WHEN country.tier <= 2 THEN 0 WHEN country.tier = 3 THEN 1 ELSE 2 END - v_band),
        CASE WHEN progress.question_id IS NULL THEN 0 ELSE 1 END,
        country.tier,
        random()
    LIMIT 1;

    IF v_question_id IS NULL THEN
        RETURN NULL;
    END IF;

    UPDATE cl_api_schema.silhouette_game_player_state
    SET current_question_id = v_question_id,
        current_hints_used = COALESCE((
            SELECT hints_revealed FROM cl_api_schema.silhouette_game_progress
            WHERE user_id = p_user_id AND question_id = v_question_id
        ), 0),
        current_attempts = 0,
        round_number = round_number + 1,
        updated_at = NOW()
    WHERE user_id = p_user_id;

    INSERT INTO cl_api_schema.silhouette_game_progress (user_id, question_id)
    VALUES (p_user_id, v_question_id)
    ON CONFLICT (user_id, question_id) DO UPDATE
    SET last_seen_at = NOW();

    RETURN v_question_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_reveal_hint(p_user_id BIGINT, p_round BIGINT)
RETURNS SMALLINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_question_id BIGINT;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS NULL OR v_state.round_number <> p_round THEN
        RAISE EXCEPTION 'Silhouette is no longer current' USING ERRCODE = 'CT409';
    END IF;
    v_question_id := v_state.current_question_id;
    IF v_state.current_hints_used >= (
        SELECT COUNT(*) FROM cl_schema.silhouette_game_hint WHERE question_id = v_question_id
    ) THEN
        RETURN v_state.current_hints_used;
    END IF;
    UPDATE cl_api_schema.silhouette_game_player_state
    SET current_hints_used = current_hints_used + 1, updated_at = NOW()
    WHERE user_id = p_user_id
    RETURNING current_hints_used INTO v_state.current_hints_used;
    UPDATE cl_api_schema.silhouette_game_progress
    SET hints_revealed = GREATEST(hints_revealed, v_state.current_hints_used),
        last_seen_at = NOW()
    WHERE user_id = p_user_id AND question_id = v_question_id;
    RETURN v_state.current_hints_used;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_skip(p_user_id BIGINT, p_round BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_question_id BIGINT;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS NULL OR v_state.round_number <> p_round THEN
        RAISE EXCEPTION 'Silhouette is no longer current' USING ERRCODE = 'CT409';
    END IF;
    v_question_id := v_state.current_question_id;
    UPDATE cl_api_schema.silhouette_game_progress
    SET skip_count = skip_count + 1, last_skipped_at = NOW(),
        deferred_until_round = v_state.round_number + LEAST(skip_count + 3, 8)
    WHERE user_id = p_user_id AND question_id = v_question_id;
    UPDATE cl_api_schema.silhouette_game_player_state
    SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
    WHERE user_id = p_user_id;
    RETURN cl_api_schema.silhouette_game_next_question(p_user_id);
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_show_answer(p_user_id BIGINT, p_round BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_question_id BIGINT;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS NULL OR v_state.round_number <> p_round THEN
        RAISE EXCEPTION 'Silhouette is no longer current' USING ERRCODE = 'CT409';
    END IF;
    v_question_id := v_state.current_question_id;
    UPDATE cl_api_schema.silhouette_game_progress
    SET reveal_count = reveal_count + 1, last_revealed_at = NOW(), last_seen_at = NOW(),
        deferred_until_round = v_state.round_number + 12
    WHERE user_id = p_user_id AND question_id = v_question_id;
    UPDATE cl_api_schema.silhouette_game_player_state
    SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
    WHERE user_id = p_user_id;
    RETURN v_question_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_guess(
    p_user_id BIGINT,
    p_round BIGINT,
    p_answer_iso2 TEXT
)
RETURNS TABLE(correct BOOLEAN, xp_awarded INTEGER, attempts INTEGER, answer_question_id BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_country cl_schema.silhouette_game_question%ROWTYPE;
    v_progress cl_api_schema.silhouette_game_progress%ROWTYPE;
    v_question_id BIGINT;
    v_correct BOOLEAN;
    v_xp INTEGER := 0;
    v_base_xp INTEGER;
    v_hint_penalty INTEGER;
    v_hint_units INTEGER;
    v_level_divisor INTEGER;
BEGIN
    IF p_answer_iso2 IS NULL OR NOT EXISTS (
        SELECT 1 FROM cl_schema.silhouette_game_question WHERE iso2 = UPPER(BTRIM(p_answer_iso2)) AND is_active
    ) THEN
        RAISE EXCEPTION 'Choose a country from the list' USING ERRCODE = 'CT400';
    END IF;

    SELECT * INTO v_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS NULL OR v_state.round_number <> p_round THEN
        RAISE EXCEPTION 'Silhouette is no longer current' USING ERRCODE = 'CT409';
    END IF;
    v_question_id := v_state.current_question_id;

    SELECT * INTO v_country FROM cl_schema.silhouette_game_question WHERE id = v_question_id;
    SELECT * INTO v_progress FROM cl_api_schema.silhouette_game_progress
    WHERE user_id = p_user_id AND question_id = v_question_id FOR UPDATE;

    v_correct := v_country.iso2 = UPPER(BTRIM(p_answer_iso2));

    INSERT INTO cl_api_schema.silhouette_game_attempt (user_id, round_number, guess_iso2, question_id, correct)
    VALUES (p_user_id, v_state.round_number, UPPER(BTRIM(p_answer_iso2)), v_question_id, v_correct)
    ON CONFLICT (user_id, round_number, guess_iso2) DO NOTHING;

    UPDATE cl_api_schema.silhouette_game_progress progress
    SET attempt_count = progress.attempt_count + 1,
        first_attempt_at = COALESCE(progress.first_attempt_at, NOW()),
        first_solved_at = CASE WHEN v_correct THEN COALESCE(progress.first_solved_at, NOW()) ELSE progress.first_solved_at END,
        best_hints_used = CASE WHEN v_correct
            THEN LEAST(COALESCE(progress.best_hints_used, 4), v_state.current_hints_used)
            ELSE progress.best_hints_used END,
        last_seen_at = NOW()
    WHERE progress.user_id = p_user_id AND progress.question_id = v_question_id;

    IF v_correct THEN
        IF v_progress.first_solved_at IS NULL THEN
            SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'silhouette_game_base_xp'), 150),
                   COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'silhouette_game_hint_penalty_percent'), 8),
                   COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'level_divisor'), 100)
            INTO v_base_xp, v_hint_penalty, v_level_divisor
            FROM cl_api_schema.app_settings
            WHERE key IN ('silhouette_game_base_xp', 'silhouette_game_hint_penalty_percent', 'level_divisor');

            SELECT COALESCE(SUM(cost_units), 0)::INTEGER INTO v_hint_units
            FROM cl_schema.silhouette_game_hint
            WHERE question_id = v_question_id AND position <= v_state.current_hints_used;
            v_xp := GREATEST(0, FLOOR(v_base_xp * (100 - v_hint_penalty * v_hint_units) / 100.0)::INTEGER);
            IF cl_api_schema.record_user_xp_event(
                    NEXTVAL('cl_api_schema.silhouette_game_xp_event_id_seq'),
                    p_user_id, NULL, v_xp, 'SILHOUETTE_GAME', v_question_id, NULL, NOW()) THEN
                INSERT INTO cl_api_schema.user_profiles (user_id) VALUES (p_user_id)
                ON CONFLICT (user_id) DO NOTHING;
                UPDATE cl_api_schema.user_profiles profile
                SET total_xp = profile.total_xp + v_xp,
                    current_level = GREATEST(profile.current_level,
                        FLOOR(SQRT((profile.total_xp + v_xp)::DOUBLE PRECISION
                            / GREATEST(v_level_divisor, 1)))::INTEGER + 1),
                    updated_at = NOW()
                WHERE profile.user_id = p_user_id;
                UPDATE cl_api_schema.silhouette_game_progress
                SET xp_awarded = v_xp
                WHERE user_id = p_user_id AND question_id = v_question_id;
            ELSE
                v_xp := 0;
            END IF;
        END IF;

        UPDATE cl_api_schema.silhouette_game_player_state
        SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
        WHERE user_id = p_user_id;
    ELSE
        UPDATE cl_api_schema.silhouette_game_player_state
        SET current_attempts = current_attempts + 1, updated_at = NOW()
        WHERE user_id = p_user_id;
    END IF;

    RETURN QUERY SELECT v_correct, v_xp, (v_state.current_attempts + 1)::INTEGER, v_question_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.silhouette_game_claim_guest(
    p_guest_token TEXT,
    p_target_user_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_guest_user_id BIGINT;
    v_guest_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_target_state cl_api_schema.silhouette_game_player_state%ROWTYPE;
    v_guest_progress cl_api_schema.silhouette_game_progress%ROWTYPE;
    v_guest_xp INTEGER := 0;
    v_transferred_xp INTEGER := 0;
    v_progress_xp INTEGER;
    v_merged_round BIGINT;
    v_level_divisor INTEGER;
    v_current_question_id BIGINT;
    v_attempts INTEGER := 0;
    v_hints SMALLINT := 0;
BEGIN
    IF p_guest_token IS NULL OR p_target_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    SELECT id INTO v_guest_user_id
    FROM cl_api_schema.users
    WHERE guest_token = p_guest_token AND is_guest AND guest_token_expires_at > NOW()
    FOR UPDATE;
    IF v_guest_user_id IS NULL OR v_guest_user_id = p_target_user_id THEN
        RETURN FALSE;
    END IF;
    PERFORM 1 FROM cl_api_schema.users
    WHERE id = p_target_user_id AND enabled AND NOT is_guest FOR UPDATE;
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_guest_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = v_guest_user_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;
    SELECT * INTO v_target_state FROM cl_api_schema.silhouette_game_player_state
    WHERE user_id = p_target_user_id FOR UPDATE;

    v_merged_round := GREATEST(COALESCE(v_target_state.round_number, 0), v_guest_state.round_number);

    UPDATE cl_api_schema.silhouette_game_progress progress
    SET deferred_until_round = v_merged_round + (progress.deferred_until_round - v_target_state.round_number)
    WHERE progress.user_id = p_target_user_id
      AND progress.deferred_until_round > v_target_state.round_number;

    UPDATE cl_api_schema.silhouette_game_progress progress
    SET hints_revealed = GREATEST(progress.hints_revealed, v_target_state.current_hints_used)
    WHERE progress.user_id = p_target_user_id
      AND progress.question_id = v_target_state.current_question_id;
    UPDATE cl_api_schema.silhouette_game_progress progress
    SET hints_revealed = GREATEST(progress.hints_revealed, v_guest_state.current_hints_used)
    WHERE progress.user_id = v_guest_user_id
      AND progress.question_id = v_guest_state.current_question_id;

    FOR v_guest_progress IN
        SELECT * FROM cl_api_schema.silhouette_game_progress
        WHERE user_id = v_guest_user_id FOR UPDATE
    LOOP
        v_guest_xp := v_guest_xp + v_guest_progress.xp_awarded;
        v_progress_xp := 0;
        IF v_guest_progress.xp_awarded > 0 AND NOT EXISTS (
            SELECT 1 FROM cl_api_schema.silhouette_game_progress target
            WHERE target.user_id = p_target_user_id
              AND target.question_id = v_guest_progress.question_id
              AND target.first_solved_at IS NOT NULL
        ) AND cl_api_schema.record_user_xp_event(
            NEXTVAL('cl_api_schema.silhouette_game_xp_event_id_seq'),
            p_target_user_id, NULL, v_guest_progress.xp_awarded,
            'SILHOUETTE_GAME', v_guest_progress.question_id, NULL,
            COALESCE(v_guest_progress.first_solved_at, NOW())) THEN
            v_transferred_xp := v_transferred_xp + v_guest_progress.xp_awarded;
            v_progress_xp := v_guest_progress.xp_awarded;
        END IF;

        INSERT INTO cl_api_schema.silhouette_game_progress (
            user_id, question_id, first_seen_at, last_seen_at, first_attempt_at, first_solved_at,
            last_skipped_at, last_revealed_at, attempt_count, skip_count, reveal_count,
            hints_revealed, best_hints_used, xp_awarded, deferred_until_round
        ) VALUES (
            p_target_user_id, v_guest_progress.question_id,
            v_guest_progress.first_seen_at, v_guest_progress.last_seen_at,
            v_guest_progress.first_attempt_at, v_guest_progress.first_solved_at,
            v_guest_progress.last_skipped_at, v_guest_progress.last_revealed_at,
            v_guest_progress.attempt_count, v_guest_progress.skip_count, v_guest_progress.reveal_count,
            v_guest_progress.hints_revealed, v_guest_progress.best_hints_used, v_progress_xp,
            CASE WHEN v_guest_progress.deferred_until_round > v_guest_state.round_number
                THEN v_merged_round + v_guest_progress.deferred_until_round - v_guest_state.round_number
                ELSE 0 END
        ) ON CONFLICT (user_id, question_id) DO UPDATE SET
            first_seen_at = LEAST(silhouette_game_progress.first_seen_at, EXCLUDED.first_seen_at),
            last_seen_at = GREATEST(silhouette_game_progress.last_seen_at, EXCLUDED.last_seen_at),
            first_attempt_at = LEAST(silhouette_game_progress.first_attempt_at, EXCLUDED.first_attempt_at),
            first_solved_at = LEAST(silhouette_game_progress.first_solved_at, EXCLUDED.first_solved_at),
            last_skipped_at = GREATEST(silhouette_game_progress.last_skipped_at, EXCLUDED.last_skipped_at),
            last_revealed_at = GREATEST(silhouette_game_progress.last_revealed_at, EXCLUDED.last_revealed_at),
            attempt_count = silhouette_game_progress.attempt_count + EXCLUDED.attempt_count,
            skip_count = silhouette_game_progress.skip_count + EXCLUDED.skip_count,
            reveal_count = silhouette_game_progress.reveal_count + EXCLUDED.reveal_count,
            hints_revealed = GREATEST(silhouette_game_progress.hints_revealed, EXCLUDED.hints_revealed),
            best_hints_used = LEAST(silhouette_game_progress.best_hints_used, EXCLUDED.best_hints_used),
            xp_awarded = CASE WHEN silhouette_game_progress.first_solved_at IS NULL
                THEN EXCLUDED.xp_awarded ELSE silhouette_game_progress.xp_awarded END,
            deferred_until_round = GREATEST(
                silhouette_game_progress.deferred_until_round, EXCLUDED.deferred_until_round);
    END LOOP;

    SELECT candidate.question_id, candidate.attempts INTO v_current_question_id, v_attempts
    FROM (
        VALUES (1, v_target_state.current_question_id, v_target_state.current_attempts),
               (2, v_guest_state.current_question_id, v_guest_state.current_attempts)
    ) AS candidate(priority, question_id, attempts)
    JOIN cl_api_schema.silhouette_game_progress progress
        ON progress.user_id = p_target_user_id AND progress.question_id = candidate.question_id
    WHERE progress.first_solved_at IS NULL
    ORDER BY candidate.priority
    LIMIT 1;

    IF v_current_question_id IS NOT NULL THEN
        SELECT hints_revealed INTO v_hints FROM cl_api_schema.silhouette_game_progress
        WHERE user_id = p_target_user_id AND question_id = v_current_question_id;

        UPDATE cl_api_schema.silhouette_game_attempt attempt
        SET round_number = v_merged_round
        WHERE attempt.user_id = p_target_user_id
          AND attempt.round_number = v_target_state.round_number
          AND attempt.question_id = v_current_question_id
          AND v_target_state.round_number <> v_merged_round;

        INSERT INTO cl_api_schema.silhouette_game_attempt (
            user_id, round_number, guess_iso2, question_id, correct, attempted_at
        )
        SELECT p_target_user_id, v_merged_round, attempt.guess_iso2, attempt.question_id, attempt.correct, attempt.attempted_at
        FROM cl_api_schema.silhouette_game_attempt attempt
        WHERE attempt.user_id = v_guest_user_id
          AND attempt.round_number = v_guest_state.round_number
          AND attempt.question_id = v_current_question_id
        ON CONFLICT (user_id, round_number, guess_iso2) DO NOTHING;
    END IF;

    INSERT INTO cl_api_schema.silhouette_game_player_state (
        user_id, current_question_id, current_hints_used, current_attempts, round_number
    ) VALUES (
        p_target_user_id, v_current_question_id, COALESCE(v_hints, 0), COALESCE(v_attempts, 0), v_merged_round
    ) ON CONFLICT (user_id) DO UPDATE SET
        current_question_id = EXCLUDED.current_question_id,
        current_hints_used = EXCLUDED.current_hints_used,
        current_attempts = EXCLUDED.current_attempts,
        round_number = EXCLUDED.round_number,
        updated_at = NOW();

    SELECT COALESCE(MAX(value::INTEGER), 100) INTO v_level_divisor
    FROM cl_api_schema.app_settings WHERE key = 'level_divisor';

    IF v_transferred_xp > 0 THEN
        INSERT INTO cl_api_schema.user_profiles (user_id) VALUES (p_target_user_id)
        ON CONFLICT (user_id) DO NOTHING;
        UPDATE cl_api_schema.user_profiles profile
        SET total_xp = profile.total_xp + v_transferred_xp,
            current_level = GREATEST(profile.current_level,
                FLOOR(SQRT((profile.total_xp + v_transferred_xp)::DOUBLE PRECISION
                    / GREATEST(v_level_divisor, 1)))::INTEGER + 1),
            updated_at = NOW()
        WHERE user_id = p_target_user_id;
    END IF;

    DELETE FROM cl_api_schema.user_xp_events
    WHERE user_id = v_guest_user_id AND source_type = 'SILHOUETTE_GAME';
    DELETE FROM cl_api_schema.weekly_xp_snapshots WHERE user_id = v_guest_user_id;
    INSERT INTO cl_api_schema.weekly_xp_snapshots (user_id, week_start, xp_earned)
    SELECT user_id, week_start, SUM(xp_awarded)::INTEGER
    FROM cl_api_schema.user_xp_events WHERE user_id = v_guest_user_id
    GROUP BY user_id, week_start;
    UPDATE cl_api_schema.user_profiles profile
    SET total_xp = GREATEST(0, profile.total_xp - v_guest_xp),
        current_level = FLOOR(SQRT(GREATEST(0, profile.total_xp - v_guest_xp)::DOUBLE PRECISION
            / GREATEST(v_level_divisor, 1)))::INTEGER + 1,
        updated_at = NOW()
    WHERE user_id = v_guest_user_id;

    DELETE FROM cl_api_schema.silhouette_game_player_state WHERE user_id = v_guest_user_id;
    DELETE FROM cl_api_schema.silhouette_game_progress WHERE user_id = v_guest_user_id;
    RETURN TRUE;
END;
$$;

INSERT INTO cl_api_schema.badge_categories (id, name, display_order)
VALUES (720000000000000001, 'Silhouettes', 60)
ON CONFLICT (name) DO UPDATE SET display_order = EXCLUDED.display_order, updated_at = NOW();

INSERT INTO cl_api_schema.badges (
    id, slug, image_url, is_secret, is_active, grant_type, grant_config,
    display_order, accent_color_key, effect_color_key, category_id
)
VALUES
    (720000000000000101, 'silhouette-30-guessed', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"SILHOUETTE_UNIQUE_SOLVED","threshold":30}'::JSONB,
     601, 'emerald-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Silhouettes')),
    (720000000000000102, 'silhouette-100-guessed', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"SILHOUETTE_UNIQUE_SOLVED","threshold":100}'::JSONB,
     602, 'sky-core', 'sky-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Silhouettes')),
    (720000000000000103, 'silhouette-globe-complete', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"SILHOUETTE_GLOBE_COMPLETED","threshold":1}'::JSONB,
     603, 'violet-core', 'violet-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Silhouettes'))
ON CONFLICT (slug) DO UPDATE SET
    is_active = TRUE, grant_type = EXCLUDED.grant_type,
    grant_config = EXCLUDED.grant_config, display_order = EXCLUDED.display_order,
    accent_color_key = EXCLUDED.accent_color_key,
    effect_color_key = EXCLUDED.effect_color_key,
    category_id = EXCLUDED.category_id, updated_at = NOW();

WITH translations(translation_id, slug, locale, name, unlocked, locked) AS (
    VALUES
    (720000000000001011, 'silhouette-30-guessed', 'en', 'Shape Spotter', 'Guessed 30 countries by their silhouette.', 'Guess 30 countries by their silhouette.'),
    (720000000000001012, 'silhouette-30-guessed', 'ru', 'Знаток контуров', 'Угадано 30 стран по силуэту.', 'Угадайте 30 стран по силуэту.'),
    (720000000000001013, 'silhouette-30-guessed', 'cs', 'Znalec obrysů', 'Uhodli jste 30 zemí podle siluety.', 'Uhodněte 30 zemí podle siluety.'),
    (720000000000001021, 'silhouette-100-guessed', 'en', 'Border Master', 'Guessed 100 countries by their silhouette.', 'Guess 100 countries by their silhouette.'),
    (720000000000001022, 'silhouette-100-guessed', 'ru', 'Мастер границ', 'Угадано 100 стран по силуэту.', 'Угадайте 100 стран по силуэту.'),
    (720000000000001023, 'silhouette-100-guessed', 'cs', 'Mistr hranic', 'Uhodli jste 100 zemí podle siluety.', 'Uhodněte 100 zemí podle siluety.'),
    (720000000000001031, 'silhouette-globe-complete', 'en', 'Whole World', 'Painted every country on the globe.', 'Guess every country on the silhouette globe.'),
    (720000000000001032, 'silhouette-globe-complete', 'ru', 'Весь мир', 'Закрашены все страны на глобусе.', 'Угадайте все страны на глобусе силуэтов.'),
    (720000000000001033, 'silhouette-globe-complete', 'cs', 'Celý svět', 'Vybarvili jste všechny země na glóbu.', 'Uhodněte všechny země na glóbu siluet.')
)
INSERT INTO cl_api_schema.badge_translations (
    id, badge_id, locale, name, description_unlocked, description_locked
)
SELECT translations.translation_id, badge.id, translations.locale,
       translations.name, translations.unlocked, translations.locked
FROM translations
JOIN cl_api_schema.badges badge ON badge.slug = translations.slug
ON CONFLICT (badge_id, locale) DO UPDATE SET
    name = EXCLUDED.name,
    description_unlocked = EXCLUDED.description_unlocked,
    description_locked = EXCLUDED.description_locked;
