CREATE TABLE cl_api_schema.river_game_player_state (
    user_id             BIGINT      PRIMARY KEY REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    current_question_id BIGINT      REFERENCES cl_schema.river_game_question (id),
    current_hints_used  SMALLINT    NOT NULL DEFAULT 0,
    current_attempts    INTEGER     NOT NULL DEFAULT 0,
    round_number        BIGINT      NOT NULL DEFAULT 0,
    cycle_number        INTEGER     NOT NULL DEFAULT 1,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_river_game_state_hints CHECK (current_hints_used BETWEEN 0 AND 3),
    CONSTRAINT chk_river_game_state_attempts CHECK (current_attempts >= 0),
    CONSTRAINT chk_river_game_state_round CHECK (round_number >= 0 AND cycle_number > 0),
    CONSTRAINT chk_river_game_state_empty CHECK (
        current_question_id IS NOT NULL OR (current_hints_used = 0 AND current_attempts = 0)
    )
);

COMMENT ON TABLE cl_api_schema.river_game_player_state IS
    'One durable current river question per guest or registered user; no per-user Redis queue.';

CREATE TABLE cl_api_schema.river_game_progress (
    user_id              BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    question_id          BIGINT      NOT NULL REFERENCES cl_schema.river_game_question (id),
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
    solve_count          INTEGER     NOT NULL DEFAULT 0,
    best_hints_used      SMALLINT,
    xp_awarded           INTEGER     NOT NULL DEFAULT 0,
    deferred_until_round BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, question_id),
    CONSTRAINT chk_river_game_progress_counts CHECK (
        attempt_count >= 0 AND skip_count >= 0 AND reveal_count >= 0 AND solve_count >= 0 AND xp_awarded >= 0
        AND deferred_until_round >= 0
        AND hints_revealed BETWEEN 0 AND 3
        AND (best_hints_used IS NULL OR best_hints_used BETWEEN 0 AND 3)
    ),
    CONSTRAINT chk_river_game_progress_attempt CHECK (
        (attempt_count = 0 AND first_attempt_at IS NULL)
        OR (attempt_count > 0 AND first_attempt_at IS NOT NULL)
    ),
    CONSTRAINT chk_river_game_progress_reveal CHECK (
        (reveal_count = 0 AND last_revealed_at IS NULL)
        OR (reveal_count > 0 AND last_revealed_at IS NOT NULL)
    ),
    CONSTRAINT chk_river_game_progress_solve CHECK (
        (solve_count = 0 AND first_solved_at IS NULL AND best_hints_used IS NULL)
        OR (solve_count > 0 AND first_solved_at IS NOT NULL AND best_hints_used IS NOT NULL)
    )
);

CREATE INDEX idx_river_game_progress_user_selection
    ON cl_api_schema.river_game_progress (user_id, first_solved_at, last_skipped_at);

CREATE INDEX idx_river_game_progress_question_difficulty
    ON cl_api_schema.river_game_progress (question_id, first_attempt_at, first_solved_at);

COMMENT ON TABLE cl_api_schema.river_game_progress IS
    'One row per player and river question. Merges use this key to avoid duplicate progress and XP.';

CREATE VIEW cl_api_schema.user_river_stats AS
SELECT progress.user_id,
       COUNT(DISTINCT question.river_key)
           FILTER (WHERE progress.first_attempt_at IS NOT NULL) AS attempted_rivers,
       COUNT(DISTINCT question.river_key)
           FILTER (WHERE progress.first_solved_at IS NOT NULL) AS solved_rivers,
       COALESCE(SUM(progress.attempt_count), 0) AS answer_attempts,
       COALESCE(SUM(progress.skip_count), 0) AS skips
FROM cl_api_schema.river_game_progress progress
JOIN cl_schema.river_game_question question ON question.id = progress.question_id
GROUP BY progress.user_id;

COMMENT ON VIEW cl_api_schema.user_river_stats IS
    'Per-player river counts derived from question progress; unique rivers are counted once across countries and guest merges.';

INSERT INTO cl_api_schema.app_settings (key, value)
VALUES ('river_game_base_xp', '200'),
       ('river_game_hint_penalty_percent', '15')
ON CONFLICT (key) DO NOTHING;

CREATE SEQUENCE cl_api_schema.river_game_xp_event_id_seq START WITH -1 INCREMENT BY -1;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_next_question(p_user_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.river_game_player_state%ROWTYPE;
    v_target_rank INTEGER := 1;
    v_total_solved INTEGER;
    v_recent_hints NUMERIC;
    v_recent_skips NUMERIC;
    v_question_id BIGINT;
    v_has_unsolved BOOLEAN;
    v_has_unseen BOOLEAN;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cl_api_schema.users WHERE id = p_user_id AND enabled) THEN
        RAISE EXCEPTION 'River game player is unavailable' USING ERRCODE = 'CT409';
    END IF;

    INSERT INTO cl_api_schema.river_game_player_state (user_id)
    VALUES (p_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT * INTO v_state
    FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_state.current_question_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM cl_schema.river_game_question
        WHERE id = v_state.current_question_id AND is_active
    ) THEN
        RETURN v_state.current_question_id;
    END IF;

    SELECT COUNT(DISTINCT question.river_key)::INTEGER INTO v_total_solved
    FROM cl_api_schema.river_game_progress progress
    JOIN cl_schema.river_game_question question ON question.id = progress.question_id
    WHERE progress.user_id = p_user_id AND progress.first_solved_at IS NOT NULL;

    SELECT AVG(recent.best_hints_used),
           AVG(CASE WHEN recent.last_skipped_at IS NULL THEN 0 ELSE 1 END)
    INTO v_recent_hints, v_recent_skips
    FROM (
        SELECT progress.best_hints_used, progress.last_skipped_at
        FROM cl_api_schema.river_game_progress progress
        WHERE progress.user_id = p_user_id AND progress.first_solved_at IS NOT NULL
        ORDER BY progress.first_solved_at DESC
        LIMIT 12
    ) recent;

    -- Mix bands instead of exhausting all easy rivers first or jumping straight to hard.
    IF v_total_solved < 5 THEN
        v_target_rank := CASE WHEN MOD(v_state.round_number, 5) = 4 THEN 2 ELSE 1 END;
    ELSIF v_total_solved < 20 THEN
        v_target_rank := CASE MOD(v_state.round_number, 10)
            WHEN 4 THEN 2 WHEN 9 THEN 4 ELSE 1 END;
    ELSIF v_total_solved < 60 THEN
        v_target_rank := CASE MOD(v_state.round_number, 5)
            WHEN 2 THEN 2 WHEN 4 THEN 4 ELSE 1 END;
    ELSE
        v_target_rank := CASE MOD(v_state.round_number, 5)
            WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 4 ELSE 5 END;
    END IF;

    IF v_total_solved >= 4 AND v_recent_hints <= 1.2
            AND COALESCE(v_recent_skips, 0) < 0.25 THEN
        v_target_rank := LEAST(5, v_target_rank + 1);
    ELSIF v_recent_hints >= 2.3 OR COALESCE(v_recent_skips, 0) >= 0.5 THEN
        v_target_rank := GREATEST(1, v_target_rank - 1);
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM cl_schema.river_game_question question
        LEFT JOIN cl_api_schema.river_game_progress progress
            ON progress.question_id = question.id AND progress.user_id = p_user_id
        WHERE question.is_active AND NOT EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress prior_progress
            JOIN cl_schema.river_game_question prior_question
                ON prior_question.id = prior_progress.question_id
            WHERE prior_progress.user_id = p_user_id
              AND prior_progress.first_solved_at IS NOT NULL
              AND prior_question.river_key = question.river_key
        )
    ) INTO v_has_unsolved;

    SELECT EXISTS (
        SELECT 1 FROM cl_schema.river_game_question question
        WHERE question.is_active AND NOT EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress seen
            JOIN cl_schema.river_game_question same_river ON same_river.id = seen.question_id
            WHERE seen.user_id = p_user_id AND same_river.river_key = question.river_key
        )
    ) INTO v_has_unseen;

    SELECT candidate.id INTO v_question_id
    FROM cl_schema.river_game_question candidate
    LEFT JOIN cl_api_schema.river_game_progress progress
        ON progress.question_id = candidate.id AND progress.user_id = p_user_id
    LEFT JOIN LATERAL (
        SELECT COUNT(*) FILTER (WHERE first_attempt_at IS NOT NULL)::INTEGER AS attempted,
               COUNT(*) FILTER (WHERE first_solved_at IS NOT NULL)::INTEGER AS solved
        FROM cl_api_schema.river_game_progress global_progress
        WHERE global_progress.question_id = candidate.id
    ) global_rate ON TRUE
    WHERE candidate.is_active
    ORDER BY
        CASE WHEN v_has_unseen AND EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress seen
            JOIN cl_schema.river_game_question same_river ON same_river.id = seen.question_id
            WHERE seen.user_id = p_user_id AND same_river.river_key = candidate.river_key
        ) THEN 1 ELSE 0 END,
        CASE WHEN v_has_unsolved AND EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress solved
            JOIN cl_schema.river_game_question same_river ON same_river.id = solved.question_id
            WHERE solved.user_id = p_user_id AND solved.first_solved_at IS NOT NULL
              AND same_river.river_key = candidate.river_key
        ) THEN 1 ELSE 0 END,
        CASE WHEN v_has_unsolved AND EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress deferred
            JOIN cl_schema.river_game_question same_river ON same_river.id = deferred.question_id
            WHERE deferred.user_id = p_user_id AND deferred.deferred_until_round > v_state.round_number
              AND same_river.river_key = candidate.river_key
        ) THEN 1 ELSE 0 END,
        ABS(candidate.rank_in_country - v_target_rank),
        CASE WHEN v_target_rank = 1
            THEN -((global_rate.solved + 3.0) / (global_rate.attempted + 5.0))
            ELSE ((global_rate.solved + 3.0) / (global_rate.attempted + 5.0))
        END + random() * 0.4,
        random()
    LIMIT 1;

    IF v_question_id IS NULL THEN
        UPDATE cl_api_schema.river_game_player_state
        SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0,
            updated_at = NOW()
        WHERE user_id = p_user_id;
        RETURN NULL;
    END IF;

    UPDATE cl_api_schema.river_game_player_state
    SET current_question_id = v_question_id,
        current_hints_used = COALESCE((
            SELECT MAX(progress.hints_revealed)
            FROM cl_api_schema.river_game_progress progress
            JOIN cl_schema.river_game_question previous_question
                ON previous_question.id = progress.question_id
            JOIN cl_schema.river_game_question next_question
                ON next_question.id = v_question_id
            WHERE progress.user_id = p_user_id
              AND previous_question.river_key = next_question.river_key
        ), 0),
        current_attempts = 0, round_number = round_number + 1,
        cycle_number = cycle_number + CASE WHEN v_has_unsolved THEN 0 ELSE 1 END,
        updated_at = NOW()
    WHERE user_id = p_user_id;

    INSERT INTO cl_api_schema.river_game_progress (user_id, question_id)
    VALUES (p_user_id, v_question_id)
    ON CONFLICT (user_id, question_id) DO UPDATE
    SET last_seen_at = NOW();

    RETURN v_question_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_reveal_hint(p_user_id BIGINT, p_question_id BIGINT)
RETURNS SMALLINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.river_game_player_state%ROWTYPE;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS DISTINCT FROM p_question_id THEN
        RAISE EXCEPTION 'River question is no longer current' USING ERRCODE = 'CT409';
    END IF;
    IF v_state.current_hints_used >= 3 THEN
        RETURN v_state.current_hints_used;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM cl_schema.river_game_hint
        WHERE question_id = p_question_id AND position = v_state.current_hints_used + 1
    ) THEN
        RAISE EXCEPTION 'River hint is unavailable' USING ERRCODE = 'CT409';
    END IF;
    UPDATE cl_api_schema.river_game_player_state
    SET current_hints_used = current_hints_used + 1, updated_at = NOW()
    WHERE user_id = p_user_id
    RETURNING current_hints_used INTO v_state.current_hints_used;
    UPDATE cl_api_schema.river_game_progress
    SET hints_revealed = GREATEST(hints_revealed, v_state.current_hints_used),
        last_seen_at = NOW()
    WHERE user_id = p_user_id AND question_id = p_question_id;
    RETURN v_state.current_hints_used;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_skip(p_user_id BIGINT, p_question_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.river_game_player_state%ROWTYPE;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS DISTINCT FROM p_question_id THEN
        RAISE EXCEPTION 'River question is no longer current' USING ERRCODE = 'CT409';
    END IF;
    UPDATE cl_api_schema.river_game_progress
    SET skip_count = skip_count + 1, last_skipped_at = NOW(),
        deferred_until_round = v_state.round_number + LEAST(skip_count + 3, 8)
    WHERE user_id = p_user_id AND question_id = p_question_id;
    UPDATE cl_api_schema.river_game_player_state
    SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
    WHERE user_id = p_user_id;
    RETURN cl_api_schema.river_game_next_question(p_user_id);
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_show_answer(p_user_id BIGINT, p_question_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.river_game_player_state%ROWTYPE;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS DISTINCT FROM p_question_id THEN
        RAISE EXCEPTION 'River question is no longer current' USING ERRCODE = 'CT409';
    END IF;
    UPDATE cl_api_schema.river_game_progress
    SET reveal_count = reveal_count + 1, last_revealed_at = NOW(), last_seen_at = NOW(),
        deferred_until_round = v_state.round_number + 12
    WHERE user_id = p_user_id AND question_id = p_question_id;
    UPDATE cl_api_schema.river_game_player_state
    SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
    WHERE user_id = p_user_id;
    RETURN p_question_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_guess(
    p_user_id BIGINT,
    p_question_id BIGINT,
    p_answer TEXT
)
RETURNS TABLE(correct BOOLEAN, xp_awarded INTEGER, attempts INTEGER, resolved_name TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.river_game_player_state%ROWTYPE;
    v_question cl_schema.river_game_question%ROWTYPE;
    v_progress cl_api_schema.river_game_progress%ROWTYPE;
    v_correct BOOLEAN;
    v_answer TEXT;
    v_xp INTEGER := 0;
    v_base_xp INTEGER;
    v_hint_penalty INTEGER;
    v_level_divisor INTEGER;
    v_event_recorded BOOLEAN;
BEGIN
    IF p_answer IS NULL OR LENGTH(BTRIM(p_answer)) NOT BETWEEN 1 AND 160 THEN
        RAISE EXCEPTION 'River answer must contain 1-160 characters' USING ERRCODE = 'CT400';
    END IF;

    SELECT * INTO v_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_question_id IS DISTINCT FROM p_question_id THEN
        RAISE EXCEPTION 'River question is no longer current' USING ERRCODE = 'CT409';
    END IF;

    SELECT * INTO v_question FROM cl_schema.river_game_question
    WHERE id = p_question_id AND is_active;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'River question is unavailable' USING ERRCODE = 'CT409';
    END IF;

    SELECT * INTO v_progress FROM cl_api_schema.river_game_progress
    WHERE user_id = p_user_id AND question_id = p_question_id FOR UPDATE;

    v_answer := LOWER(REGEXP_REPLACE(BTRIM(p_answer), '[[:space:][:punct:]]+', '', 'g'));
    v_correct := v_answer = LOWER(REGEXP_REPLACE(v_question.name_en, '[[:space:][:punct:]]+', '', 'g'))
        OR EXISTS (
            SELECT 1 FROM UNNEST(v_question.answer_aliases) alias
            WHERE v_answer = LOWER(REGEXP_REPLACE(alias, '[[:space:][:punct:]]+', '', 'g'))
        );

    UPDATE cl_api_schema.river_game_progress progress
    SET attempt_count = progress.attempt_count + 1,
        first_attempt_at = COALESCE(progress.first_attempt_at, NOW()),
        first_solved_at = CASE WHEN v_correct THEN COALESCE(progress.first_solved_at, NOW()) ELSE progress.first_solved_at END,
        solve_count = progress.solve_count + CASE WHEN v_correct THEN 1 ELSE 0 END,
        best_hints_used = CASE WHEN v_correct THEN LEAST(COALESCE(progress.best_hints_used, 3), v_state.current_hints_used)
            ELSE progress.best_hints_used END,
        last_seen_at = NOW()
    WHERE progress.user_id = p_user_id AND progress.question_id = p_question_id;

    IF v_correct THEN
        IF v_progress.first_solved_at IS NULL THEN
            SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'river_game_base_xp'), 200),
                   COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'river_game_hint_penalty_percent'), 15),
                   COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'level_divisor'), 100)
            INTO v_base_xp, v_hint_penalty, v_level_divisor
            FROM cl_api_schema.app_settings
            WHERE key IN ('river_game_base_xp', 'river_game_hint_penalty_percent', 'level_divisor');

            v_xp := GREATEST(0, FLOOR(v_base_xp * (100 - v_hint_penalty * v_state.current_hints_used) / 100.0)::INTEGER);
            v_event_recorded := cl_api_schema.record_user_xp_event(
                NEXTVAL('cl_api_schema.river_game_xp_event_id_seq'),
                p_user_id, NULL, v_xp, 'RIVER_GAME',
                SUBSTRING(v_question.river_key FROM 2)::BIGINT, NULL, NOW());

            IF v_event_recorded THEN
                INSERT INTO cl_api_schema.user_profiles (user_id) VALUES (p_user_id)
                ON CONFLICT (user_id) DO NOTHING;
                UPDATE cl_api_schema.user_profiles profile
                SET total_xp = profile.total_xp + v_xp,
                    current_level = GREATEST(profile.current_level,
                        FLOOR(SQRT((profile.total_xp + v_xp)::DOUBLE PRECISION
                            / GREATEST(v_level_divisor, 1)))::INTEGER + 1),
                    updated_at = NOW()
                WHERE profile.user_id = p_user_id;
                UPDATE cl_api_schema.river_game_progress
                SET xp_awarded = v_xp
                WHERE user_id = p_user_id AND question_id = p_question_id;
            ELSE
                v_xp := 0;
            END IF;
        END IF;

        UPDATE cl_api_schema.river_game_player_state
        SET current_question_id = NULL, current_hints_used = 0, current_attempts = 0, updated_at = NOW()
        WHERE user_id = p_user_id;
    ELSE
        UPDATE cl_api_schema.river_game_player_state
        SET current_attempts = current_attempts + 1, updated_at = NOW()
        WHERE user_id = p_user_id;
    END IF;

    RETURN QUERY SELECT v_correct, v_xp,
        (v_state.current_attempts + 1)::INTEGER,
        CASE WHEN v_correct THEN v_question.name_en ELSE NULL::TEXT END;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.river_game_claim_guest(
    p_guest_token TEXT,
    p_target_user_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_guest_user_id BIGINT;
    v_guest_state cl_api_schema.river_game_player_state%ROWTYPE;
    v_target_state cl_api_schema.river_game_player_state%ROWTYPE;
    v_guest_progress cl_api_schema.river_game_progress%ROWTYPE;
    v_guest_xp INTEGER := 0;
    v_transferred_xp INTEGER := 0;
    v_progress_xp INTEGER;
    v_guest_deferred_until_round BIGINT;
    v_merged_round BIGINT;
    v_level_divisor INTEGER;
    v_current_question_id BIGINT;
    v_hints SMALLINT := 0;
    v_attempts INTEGER := 0;
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

    SELECT * INTO v_guest_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = v_guest_user_id FOR UPDATE;
    SELECT * INTO v_target_state FROM cl_api_schema.river_game_player_state
    WHERE user_id = p_target_user_id FOR UPDATE;

    v_merged_round := GREATEST(
        COALESCE(v_target_state.round_number, 0),
        COALESCE(v_guest_state.round_number, 0));

    -- Keep the number of rounds remaining after a skip when the two players
    -- have reached different round numbers.
    UPDATE cl_api_schema.river_game_progress progress
    SET deferred_until_round = v_merged_round
        + (progress.deferred_until_round - v_target_state.round_number)
    WHERE progress.user_id = p_target_user_id
      AND progress.deferred_until_round > v_target_state.round_number;

    -- A current question can have revealed clues even if it has not been solved.
    -- Persist them before either player's current question is discarded.
    UPDATE cl_api_schema.river_game_progress progress
    SET hints_revealed = GREATEST(progress.hints_revealed, v_target_state.current_hints_used)
    WHERE progress.user_id = p_target_user_id
      AND progress.question_id = v_target_state.current_question_id;
    UPDATE cl_api_schema.river_game_progress progress
    SET hints_revealed = GREATEST(progress.hints_revealed, v_guest_state.current_hints_used)
    WHERE progress.user_id = v_guest_user_id
      AND progress.question_id = v_guest_state.current_question_id;

    FOR v_guest_progress IN
        SELECT * FROM cl_api_schema.river_game_progress
        WHERE user_id = v_guest_user_id FOR UPDATE
    LOOP
        v_guest_xp := v_guest_xp + v_guest_progress.xp_awarded;
        v_progress_xp := 0;
        v_guest_deferred_until_round := CASE
            WHEN v_guest_progress.deferred_until_round > COALESCE(v_guest_state.round_number, 0)
            THEN v_merged_round + v_guest_progress.deferred_until_round
                - COALESCE(v_guest_state.round_number, 0)
            ELSE 0
        END;
        IF v_guest_progress.xp_awarded > 0 AND NOT EXISTS (
            SELECT 1 FROM cl_api_schema.river_game_progress target
            WHERE target.user_id = p_target_user_id
              AND target.question_id = v_guest_progress.question_id
              AND target.first_solved_at IS NOT NULL
        ) THEN
            IF cl_api_schema.record_user_xp_event(
                NEXTVAL('cl_api_schema.river_game_xp_event_id_seq'),
                p_target_user_id, NULL, v_guest_progress.xp_awarded,
                'RIVER_GAME', (SELECT SUBSTRING(river_key FROM 2)::BIGINT
                               FROM cl_schema.river_game_question
                               WHERE id = v_guest_progress.question_id), NULL,
                COALESCE(v_guest_progress.first_solved_at, NOW())) THEN
                v_transferred_xp := v_transferred_xp + v_guest_progress.xp_awarded;
                v_progress_xp := v_guest_progress.xp_awarded;
            END IF;
        END IF;

        INSERT INTO cl_api_schema.river_game_progress (
            user_id, question_id, first_seen_at, last_seen_at, first_attempt_at,
            first_solved_at, last_skipped_at, last_revealed_at, attempt_count, skip_count,
            reveal_count, hints_revealed, solve_count, best_hints_used, xp_awarded, deferred_until_round
        ) VALUES (
            p_target_user_id, v_guest_progress.question_id,
            v_guest_progress.first_seen_at, v_guest_progress.last_seen_at,
            v_guest_progress.first_attempt_at, v_guest_progress.first_solved_at,
            v_guest_progress.last_skipped_at, v_guest_progress.last_revealed_at,
            v_guest_progress.attempt_count, v_guest_progress.skip_count,
            v_guest_progress.reveal_count, v_guest_progress.hints_revealed,
            v_guest_progress.solve_count, v_guest_progress.best_hints_used,
            v_progress_xp, v_guest_deferred_until_round
        ) ON CONFLICT (user_id, question_id) DO UPDATE SET
            first_seen_at = LEAST(river_game_progress.first_seen_at, EXCLUDED.first_seen_at),
            last_seen_at = GREATEST(river_game_progress.last_seen_at, EXCLUDED.last_seen_at),
            first_attempt_at = LEAST(river_game_progress.first_attempt_at, EXCLUDED.first_attempt_at),
            first_solved_at = LEAST(river_game_progress.first_solved_at, EXCLUDED.first_solved_at),
            last_skipped_at = GREATEST(river_game_progress.last_skipped_at, EXCLUDED.last_skipped_at),
            last_revealed_at = GREATEST(river_game_progress.last_revealed_at, EXCLUDED.last_revealed_at),
            attempt_count = river_game_progress.attempt_count + EXCLUDED.attempt_count,
            skip_count = river_game_progress.skip_count + EXCLUDED.skip_count,
            reveal_count = river_game_progress.reveal_count + EXCLUDED.reveal_count,
            hints_revealed = GREATEST(river_game_progress.hints_revealed, EXCLUDED.hints_revealed),
            solve_count = river_game_progress.solve_count + EXCLUDED.solve_count,
            best_hints_used = LEAST(river_game_progress.best_hints_used, EXCLUDED.best_hints_used),
            xp_awarded = CASE WHEN river_game_progress.first_solved_at IS NULL
                THEN EXCLUDED.xp_awarded ELSE river_game_progress.xp_awarded END,
            deferred_until_round = GREATEST(
                river_game_progress.deferred_until_round, EXCLUDED.deferred_until_round);
    END LOOP;

    IF v_target_state.current_question_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM cl_schema.river_game_question question
        JOIN cl_api_schema.river_game_progress progress
            ON progress.question_id = question.id AND progress.user_id = p_target_user_id
        WHERE question.id = v_target_state.current_question_id
          AND question.is_active AND progress.first_solved_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM cl_api_schema.river_game_progress solved
              JOIN cl_schema.river_game_question solved_question
                  ON solved_question.id = solved.question_id
              WHERE solved.user_id = p_target_user_id
                AND solved.first_solved_at IS NOT NULL
                AND solved_question.river_key = question.river_key
          )
    ) THEN
        v_current_question_id := v_target_state.current_question_id;
        v_attempts := v_target_state.current_attempts
            + CASE WHEN v_guest_state.current_question_id = v_current_question_id
                THEN v_guest_state.current_attempts ELSE 0 END;
    ELSIF v_guest_state.current_question_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM cl_schema.river_game_question question
        JOIN cl_api_schema.river_game_progress progress
            ON progress.question_id = question.id AND progress.user_id = p_target_user_id
        WHERE question.id = v_guest_state.current_question_id
          AND question.is_active AND progress.first_solved_at IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM cl_api_schema.river_game_progress solved
              JOIN cl_schema.river_game_question solved_question
                  ON solved_question.id = solved.question_id
              WHERE solved.user_id = p_target_user_id
                AND solved.first_solved_at IS NOT NULL
                AND solved_question.river_key = question.river_key
          )
    ) THEN
        v_current_question_id := v_guest_state.current_question_id;
        v_attempts := v_guest_state.current_attempts;
    END IF;

    IF v_current_question_id IS NOT NULL THEN
        SELECT COALESCE(MAX(progress.hints_revealed), 0) INTO v_hints
        FROM cl_api_schema.river_game_progress progress
        JOIN cl_schema.river_game_question seen_question
            ON seen_question.id = progress.question_id
        JOIN cl_schema.river_game_question current_question
            ON current_question.id = v_current_question_id
        WHERE progress.user_id = p_target_user_id
          AND seen_question.river_key = current_question.river_key;
    END IF;

    INSERT INTO cl_api_schema.river_game_player_state (
        user_id, current_question_id, current_hints_used, current_attempts, round_number, cycle_number
    ) VALUES (
        p_target_user_id, v_current_question_id, v_hints, v_attempts,
        v_merged_round,
        GREATEST(COALESCE(v_target_state.cycle_number, 1), COALESCE(v_guest_state.cycle_number, 1))
    ) ON CONFLICT (user_id) DO UPDATE SET
        current_question_id = EXCLUDED.current_question_id,
        current_hints_used = EXCLUDED.current_hints_used,
        current_attempts = EXCLUDED.current_attempts,
        round_number = EXCLUDED.round_number,
        cycle_number = EXCLUDED.cycle_number,
        updated_at = NOW();

    SELECT COALESCE(value::INTEGER, 100) INTO v_level_divisor
    FROM cl_api_schema.app_settings WHERE key = 'level_divisor';
    v_level_divisor := COALESCE(v_level_divisor, 100);

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
    WHERE user_id = v_guest_user_id AND source_type = 'RIVER_GAME';
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

    DELETE FROM cl_api_schema.river_game_player_state WHERE user_id = v_guest_user_id;
    DELETE FROM cl_api_schema.river_game_progress WHERE user_id = v_guest_user_id;
    IF v_current_question_id IS NULL THEN
        PERFORM cl_api_schema.river_game_next_question(p_target_user_id);
    END IF;
    RETURN TRUE;
END;
$$;

INSERT INTO cl_api_schema.badge_categories (id, name, display_order)
VALUES (710000000000000001, 'Rivers', 50)
ON CONFLICT (name) DO UPDATE SET display_order = EXCLUDED.display_order, updated_at = NOW();

INSERT INTO cl_api_schema.badges (
    id, slug, image_url, is_secret, is_active, grant_type, grant_config,
    display_order, accent_color_key, effect_color_key, category_id
)
VALUES
    (710000000000000101, 'river-5-guessed', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_SOLVED","threshold":5}'::JSONB,
     501, 'cyan-core', 'cyan-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers')),
    (710000000000000102, 'river-50-guessed', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_SOLVED","threshold":50}'::JSONB,
     502, 'sky-core', 'sky-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers')),
    (710000000000000103, 'river-100-guessed', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_SOLVED","threshold":100}'::JSONB,
     503, 'violet-core', 'violet-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers')),
    (710000000000000105, 'river-50-explored', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_ATTEMPTED","threshold":50}'::JSONB,
     504, 'emerald-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers')),
    (710000000000000106, 'river-100-explored', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_ATTEMPTED","threshold":100}'::JSONB,
     505, 'cyan-core', 'cyan-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers')),
    (710000000000000104, 'river-250-explored', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"RIVER_UNIQUE_ATTEMPTED","threshold":250}'::JSONB,
     506, 'sky-core', 'sky-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'Rivers'))
ON CONFLICT (slug) DO UPDATE SET
    is_active = TRUE, grant_type = EXCLUDED.grant_type,
    grant_config = EXCLUDED.grant_config, display_order = EXCLUDED.display_order,
    accent_color_key = EXCLUDED.accent_color_key,
    effect_color_key = EXCLUDED.effect_color_key,
    category_id = EXCLUDED.category_id, updated_at = NOW();

WITH translations(translation_id, slug, locale, name, unlocked, locked) AS (
    VALUES
    (710000000000001011, 'river-5-guessed', 'en', 'First Five Rivers', 'Guessed 5 different rivers.', 'Guess 5 different rivers.'),
    (710000000000001012, 'river-5-guessed', 'ru', 'Первые пять рек', 'Угадано 5 разных рек.', 'Угадайте 5 разных рек.'),
    (710000000000001013, 'river-5-guessed', 'cs', 'Prvních pět řek', 'Uhodli jste 5 různých řek.', 'Uhodněte 5 různých řek.'),
    (710000000000001021, 'river-50-guessed', 'en', 'River Navigator', 'Guessed 50 different rivers.', 'Guess 50 different rivers.'),
    (710000000000001022, 'river-50-guessed', 'ru', 'Речной навигатор', 'Угадано 50 разных рек.', 'Угадайте 50 разных рек.'),
    (710000000000001023, 'river-50-guessed', 'cs', 'Říční navigátor', 'Uhodli jste 50 různých řek.', 'Uhodněte 50 různých řek.'),
    (710000000000001031, 'river-100-guessed', 'en', 'River Cartographer', 'Guessed 100 different rivers.', 'Guess 100 different rivers.'),
    (710000000000001032, 'river-100-guessed', 'ru', 'Речной картограф', 'Угадано 100 разных рек.', 'Угадайте 100 разных рек.'),
    (710000000000001033, 'river-100-guessed', 'cs', 'Říční kartograf', 'Uhodli jste 100 různých řek.', 'Uhodněte 100 různých řek.'),
    (710000000000001051, 'river-50-explored', 'en', 'River Scout', 'Explored 50 different rivers.', 'Make a guess on 50 different rivers. Skips alone do not count.'),
    (710000000000001052, 'river-50-explored', 'ru', 'Речной следопыт', 'Изучено 50 разных рек.', 'Попробуйте угадать 50 разных рек. Одни пропуски не считаются.'),
    (710000000000001053, 'river-50-explored', 'cs', 'Průzkumník řek', 'Prozkoumali jste 50 různých řek.', 'Zkuste uhodnout 50 různých řek. Samotná přeskočení se nepočítají.'),
    (710000000000001061, 'river-100-explored', 'en', 'Waterway Explorer', 'Explored 100 different rivers.', 'Make a guess on 100 different rivers. Skips alone do not count.'),
    (710000000000001062, 'river-100-explored', 'ru', 'Исследователь водных путей', 'Изучено 100 разных рек.', 'Попробуйте угадать 100 разных рек. Одни пропуски не считаются.'),
    (710000000000001063, 'river-100-explored', 'cs', 'Badatel vodních cest', 'Prozkoumali jste 100 různých řek.', 'Zkuste uhodnout 100 různých řek. Samotná přeskočení se nepočítají.'),
    (710000000000001041, 'river-250-explored', 'en', 'Atlas Explorer', 'Explored 250 different rivers.', 'Make a guess on 250 different rivers. Skips alone do not count.'),
    (710000000000001042, 'river-250-explored', 'ru', 'Исследователь атласа', 'Изучено 250 разных рек.', 'Попробуйте угадать 250 разных рек. Одни пропуски не считаются.'),
    (710000000000001043, 'river-250-explored', 'cs', 'Průzkumník atlasu', 'Prozkoumali jste 250 různých řek.', 'Zkuste uhodnout 250 různých řek. Samotná přeskočení se nepočítají.')
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
