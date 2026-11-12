CREATE TABLE cl_api_schema.budget_game_hint (
    session_id BIGINT NOT NULL
        REFERENCES cl_api_schema.budget_game_session (id) ON DELETE CASCADE,
    question_variant_id BIGINT NOT NULL
        REFERENCES cl_budget_schema.question_variant (id),
    position           SMALLINT                 NOT NULL,
    hint_index         SMALLINT                 NOT NULL,
    multiplier_percent NUMERIC(5, 2)            NOT NULL,
    hint_payload       JSONB                    NOT NULL,
    revealed_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (session_id, question_variant_id, hint_index),
    CONSTRAINT uq_budget_game_hint_position UNIQUE (session_id, position, hint_index),
    CONSTRAINT chk_budget_game_hint_position CHECK (position BETWEEN 1 AND 5),
    CONSTRAINT chk_budget_game_hint_index CHECK (hint_index BETWEEN 0 AND 4),
    CONSTRAINT chk_budget_game_hint_multiplier CHECK (
        multiplier_percent > 0 AND multiplier_percent < 100
    ),
    CONSTRAINT chk_budget_game_hint_payload CHECK (JSONB_TYPEOF(hint_payload) = 'object')
);

CREATE INDEX idx_budget_game_hint_session
ON cl_api_schema.budget_game_hint (session_id, position, hint_index);

CREATE OR REPLACE FUNCTION cl_api_schema.reveal_budget_game_hint (
    p_session_id BIGINT,
    p_user_id BIGINT,
    p_question_variant_id BIGINT,
    p_hint_index SMALLINT
)
RETURNS TABLE (
    outcome TEXT,
    hint_index SMALLINT,
    hint_payload JSONB,
    effective_multiplier_percent NUMERIC(5, 2),
    revealed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_session cl_api_schema.budget_game_session%ROWTYPE;
    v_challenge_date DATE;
    v_expected_variant_id BIGINT;
    v_next_hint_index INTEGER;
    v_hint_payload JSONB;
    v_multiplier NUMERIC(5, 2);
    v_revealed_at TIMESTAMP WITH TIME ZONE;
BEGIN
    SELECT session.*
    INTO v_session
    FROM cl_api_schema.budget_game_session session
    WHERE session.id = p_session_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT
            'SESSION_NOT_FOUND'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    IF p_user_id IS NULL OR v_session.user_id <> p_user_id THEN
        RETURN QUERY SELECT
            'ACCESS_DENIED'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    SELECT challenge.challenge_date
    INTO v_challenge_date
    FROM cl_budget_schema.daily_challenge challenge
    WHERE challenge.id = v_session.challenge_id;

    IF v_challenge_date <> (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE THEN
        RETURN QUERY SELECT
            'SESSION_EXPIRED'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    IF v_session.status <> 'IN_PROGRESS' THEN
        RETURN QUERY SELECT
            'SESSION_NOT_IN_PROGRESS'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    SELECT question.question_variant_id
    INTO v_expected_variant_id
    FROM cl_budget_schema.daily_challenge_question question
    WHERE question.challenge_id = v_session.challenge_id
      AND question.position = v_session.current_position;

    IF v_expected_variant_id IS NULL OR v_expected_variant_id <> p_question_variant_id THEN
        RETURN QUERY SELECT
            'STALE_QUESTION'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    SELECT hint.hint_payload, hint.multiplier_percent, hint.revealed_at
    INTO v_hint_payload, v_multiplier, v_revealed_at
    FROM cl_api_schema.budget_game_hint hint
    WHERE hint.session_id = p_session_id
      AND hint.question_variant_id = p_question_variant_id
      AND hint.hint_index = p_hint_index;

    IF FOUND THEN
        RETURN QUERY SELECT
            'ALREADY_REVEALED'::TEXT,
            p_hint_index,
            v_hint_payload,
            (SELECT MIN(revealed.multiplier_percent)
             FROM cl_api_schema.budget_game_hint revealed
             WHERE revealed.session_id = p_session_id
               AND revealed.question_variant_id = p_question_variant_id),
            v_revealed_at;
        RETURN;
    END IF;

    SELECT COUNT(*)::INTEGER
    INTO v_next_hint_index
    FROM cl_api_schema.budget_game_hint hint
    WHERE hint.session_id = p_session_id
      AND hint.question_variant_id = p_question_variant_id;

    IF p_hint_index IS NULL OR p_hint_index <> v_next_hint_index THEN
        RETURN QUERY SELECT
            'STALE_HINT'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    SELECT variant.reveal_payload -> 'hints' -> p_hint_index
    INTO v_hint_payload
    FROM cl_budget_schema.question_variant variant
    WHERE variant.id = p_question_variant_id;

    IF v_hint_payload IS NULL OR JSONB_TYPEOF(v_hint_payload) <> 'object' THEN
        RETURN QUERY SELECT
            'HINT_NOT_AVAILABLE'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    v_multiplier := (v_hint_payload ->> 'multiplierPercent')::NUMERIC;
    IF v_multiplier IS NULL OR v_multiplier <= 0 OR v_multiplier >= 100 THEN
        RETURN QUERY SELECT
            'INVALID_HINT'::TEXT, NULL::SMALLINT, NULL::JSONB,
            NULL::NUMERIC(5, 2), NULL::TIMESTAMP WITH TIME ZONE;
        RETURN;
    END IF;

    INSERT INTO cl_api_schema.budget_game_hint (
        session_id,
        question_variant_id,
        position,
        hint_index,
        multiplier_percent,
        hint_payload
    )
    VALUES (
        p_session_id,
        p_question_variant_id,
        v_session.current_position,
        p_hint_index,
        v_multiplier,
        v_hint_payload
    )
    RETURNING budget_game_hint.revealed_at INTO v_revealed_at;

    RETURN QUERY SELECT
        'REVEALED'::TEXT,
        p_hint_index,
        v_hint_payload,
        (SELECT MIN(revealed.multiplier_percent)
         FROM cl_api_schema.budget_game_hint revealed
         WHERE revealed.session_id = p_session_id
           AND revealed.question_variant_id = p_question_variant_id),
        v_revealed_at;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.submit_budget_game_answer (
    p_answer_id BIGINT,
    p_session_id BIGINT,
    p_user_id BIGINT,
    p_question_variant_id BIGINT,
    p_guessed_value NUMERIC,
    p_guessed_choice TEXT
)
RETURNS TABLE (
    outcome TEXT,
    session_id BIGINT,
    question_variant_id BIGINT,
    answer_position SMALLINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_session cl_api_schema.budget_game_session%ROWTYPE;
    v_challenge_date DATE;
    v_expected_variant_id BIGINT;
    v_question_type TEXT;
    v_answer_value NUMERIC;
    v_answer_unit TEXT;
    v_answer_choice TEXT;
    v_reveal_payload JSONB;
    v_accuracy_fraction NUMERIC;
    v_relative_error NUMERIC;
    v_scoring_fraction NUMERIC;
    v_accuracy_percent NUMERIC(5, 2);
    v_raw_xp_percent NUMERIC(5, 2);
    v_raw_xp_earned INTEGER;
    v_hint_multiplier_percent NUMERIC(5, 2);
    v_hints_used SMALLINT;
    v_xp_percent NUMERIC(5, 2);
    v_xp_earned INTEGER;
    v_existing_position SMALLINT;
BEGIN
    SELECT session.*
    INTO v_session
    FROM cl_api_schema.budget_game_session session
    JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = session.challenge_id
    WHERE session.id = p_session_id
    FOR UPDATE OF session;

    IF NOT FOUND THEN
        RETURN QUERY SELECT 'SESSION_NOT_FOUND'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
        RETURN;
    END IF;

    SELECT challenge.challenge_date
    INTO v_challenge_date
    FROM cl_budget_schema.daily_challenge challenge
    WHERE challenge.id = v_session.challenge_id;

    IF p_user_id IS NULL OR v_session.user_id <> p_user_id THEN
        RETURN QUERY SELECT 'ACCESS_DENIED'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
        RETURN;
    END IF;

    SELECT answer.position
    INTO v_existing_position
    FROM cl_api_schema.budget_game_answer answer
    WHERE answer.session_id = p_session_id
      AND answer.question_variant_id = p_question_variant_id;

    IF v_existing_position IS NOT NULL THEN
        RETURN QUERY SELECT 'ALREADY_ANSWERED'::TEXT, p_session_id, p_question_variant_id, v_existing_position;
        RETURN;
    END IF;

    IF v_challenge_date <> (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE THEN
        IF v_session.status = 'IN_PROGRESS' THEN
            UPDATE cl_api_schema.budget_game_session session
            SET status = 'ABANDONED', updated_at = NOW()
            WHERE session.id = p_session_id;
        END IF;
        RETURN QUERY SELECT 'SESSION_EXPIRED'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
        RETURN;
    END IF;

    IF v_session.status <> 'IN_PROGRESS' THEN
        RETURN QUERY SELECT 'SESSION_NOT_IN_PROGRESS'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
        RETURN;
    END IF;

    SELECT question.question_variant_id
    INTO v_expected_variant_id
    FROM cl_budget_schema.daily_challenge_question question
    WHERE question.challenge_id = v_session.challenge_id
      AND question.position = v_session.current_position;

    IF v_expected_variant_id IS NULL OR v_expected_variant_id <> p_question_variant_id THEN
        RETURN QUERY SELECT 'STALE_QUESTION'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
        RETURN;
    END IF;

    SELECT
        variant.question_type,
        variant.answer_value,
        variant.answer_unit,
        variant.answer_choice,
        variant.reveal_payload
    INTO
        v_question_type,
        v_answer_value,
        v_answer_unit,
        v_answer_choice,
        v_reveal_payload
    FROM cl_budget_schema.question_variant variant
    WHERE variant.id = v_expected_variant_id;

    IF v_question_type = 'HIGHER_LOWER' THEN
        p_guessed_choice := UPPER(BTRIM(p_guessed_choice));
        IF p_guessed_value IS NOT NULL OR p_guessed_choice NOT IN ('PRIMARY', 'COMPARISON') THEN
            RETURN QUERY SELECT 'INVALID_ANSWER'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
            RETURN;
        END IF;

        v_accuracy_percent := CASE WHEN p_guessed_choice = v_answer_choice THEN 100 ELSE 0 END;
        v_raw_xp_percent := CASE WHEN p_guessed_choice = v_answer_choice THEN 100 ELSE 0 END;
    ELSE
        IF p_guessed_value IS NULL
            OR p_guessed_value < 0
            OR (p_guessed_value = 0 AND v_question_type NOT IN ('BUDGET_OVERRUN', 'HOW_LATE'))
            OR p_guessed_choice IS NOT NULL THEN
            RETURN QUERY SELECT 'INVALID_ANSWER'::TEXT, p_session_id, p_question_variant_id, NULL::SMALLINT;
            RETURN;
        END IF;

        IF v_answer_value = 0 THEN
            v_accuracy_percent := CASE WHEN p_guessed_value = 0 THEN 100 ELSE 0 END;
            v_raw_xp_percent := CASE WHEN p_guessed_value = 0 THEN 100 ELSE 0 END;
        ELSE
            v_accuracy_fraction := LEAST(p_guessed_value, v_answer_value)
                / GREATEST(p_guessed_value, v_answer_value);
            v_accuracy_percent := ROUND(v_accuracy_fraction * 100, 2);
            v_relative_error := ABS(p_guessed_value - v_answer_value) / v_answer_value;

            IF v_relative_error <= v_session.full_xp_threshold_percent / 100 THEN
                v_raw_xp_percent := 100;
            ELSE
                v_scoring_fraction := GREATEST(
                    0,
                    1 - (
                        (v_relative_error - v_session.full_xp_threshold_percent / 100)
                        / (1 - v_session.full_xp_threshold_percent / 100)
                    )
                );
                v_raw_xp_percent := ROUND((
                    100 * POWER(
                        v_scoring_fraction::DOUBLE PRECISION,
                        v_session.xp_curve_power::DOUBLE PRECISION
                    )
                )::NUMERIC, 2);
            END IF;
        END IF;
    END IF;

    SELECT
        COUNT(*)::SMALLINT,
        COALESCE(MIN(hint.multiplier_percent), 100)::NUMERIC(5, 2)
    INTO v_hints_used, v_hint_multiplier_percent
    FROM cl_api_schema.budget_game_hint hint
    WHERE hint.session_id = p_session_id
      AND hint.question_variant_id = p_question_variant_id;

    v_xp_percent := ROUND(v_raw_xp_percent * v_hint_multiplier_percent / 100, 2);
    v_raw_xp_earned := ROUND(v_session.base_xp_per_question * v_raw_xp_percent / 100)::INTEGER;
    v_xp_earned := ROUND(v_session.base_xp_per_question * v_xp_percent / 100)::INTEGER;

    INSERT INTO cl_api_schema.budget_game_answer (
        id,
        session_id,
        position,
        question_variant_id,
        question_type,
        guessed_value,
        guessed_choice,
        answer_value,
        answer_unit,
        answer_choice,
        accuracy_percent,
        raw_xp_percent,
        raw_xp_earned,
        hint_multiplier_percent,
        hints_used,
        xp_percent,
        xp_earned,
        reveal_payload
    )
    VALUES (
        p_answer_id,
        p_session_id,
        v_session.current_position,
        p_question_variant_id,
        v_question_type,
        p_guessed_value,
        p_guessed_choice,
        v_answer_value,
        v_answer_unit,
        v_answer_choice,
        v_accuracy_percent,
        v_raw_xp_percent,
        v_raw_xp_earned,
        v_hint_multiplier_percent,
        v_hints_used,
        v_xp_percent,
        v_xp_earned,
        v_reveal_payload
    );

    UPDATE cl_api_schema.budget_game_session session
    SET
        current_position = v_session.current_position + 1,
        total_xp_earned = session.total_xp_earned + v_xp_earned,
        status = CASE WHEN v_session.current_position = 5 THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
        completed_at = CASE WHEN v_session.current_position = 5 THEN NOW() END,
        updated_at = NOW()
    WHERE session.id = p_session_id;

    IF v_session.current_position = 5 THEN
        PERFORM cl_api_schema.apply_budget_session_xp(p_session_id);
    END IF;

    RETURN QUERY SELECT 'ANSWERED'::TEXT, p_session_id, p_question_variant_id, v_session.current_position;
END;
$$;
