CREATE TABLE cl_api_schema.quiz_game_player_state (
    user_id            BIGINT      PRIMARY KEY REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    stop_number        BIGINT      NOT NULL DEFAULT 0,
    current_country_id BIGINT      REFERENCES cl_schema.quiz_game_country (id),
    current_level      SMALLINT,
    from_country_id    BIGINT      REFERENCES cl_schema.quiz_game_country (id),
    arrival_mode       VARCHAR(5),
    question_ids       BIGINT[]    NOT NULL DEFAULT '{}',
    next_country_id    BIGINT      REFERENCES cl_schema.quiz_game_country (id),
    next_level         SMALLINT,
    next_mode          VARCHAR(5),
    next_route         TEXT[]      NOT NULL DEFAULT '{}',
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quiz_game_state_stop CHECK (stop_number >= 0),
    CONSTRAINT chk_quiz_game_state_modes CHECK (
        (arrival_mode IS NULL OR arrival_mode IN ('DRIVE', 'FLY'))
        AND (next_mode IS NULL OR next_mode IN ('DRIVE', 'FLY'))
        AND ((next_country_id IS NULL) = (next_mode IS NULL))
        AND ((next_country_id IS NULL) = (next_level IS NULL))
    ),
    CONSTRAINT chk_quiz_game_state_levels CHECK (
        (current_level IS NULL OR current_level BETWEEN 1 AND 3)
        AND (next_level IS NULL OR next_level BETWEEN 1 AND 3)
        AND ((current_country_id IS NULL) = (current_level IS NULL))
    ),
    CONSTRAINT chk_quiz_game_state_empty CHECK (
        current_country_id IS NOT NULL OR (cardinality(question_ids) = 0 AND next_country_id IS NULL)
    )
);

COMMENT ON TABLE cl_api_schema.quiz_game_player_state IS
    'One durable stop of the world tour quiz per guest or registered user: the country, the level played there, its two questions and the next destination once both are answered.';

CREATE TABLE cl_api_schema.quiz_game_progress (
    user_id         BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    quiz_country_id BIGINT      NOT NULL REFERENCES cl_schema.quiz_game_country (id),
    level           SMALLINT    NOT NULL DEFAULT 0,
    visits          INTEGER     NOT NULL DEFAULT 0,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT CLOCK_TIMESTAMP(),
    last_visit_at   TIMESTAMPTZ NOT NULL DEFAULT CLOCK_TIMESTAMP(),
    explored_at     TIMESTAMPTZ,
    mastered_at     TIMESTAMPTZ,
    correct_answers SMALLINT    NOT NULL DEFAULT 0,
    xp_awarded      INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, quiz_country_id),
    CONSTRAINT chk_quiz_game_progress_level CHECK (level BETWEEN 0 AND 3),
    CONSTRAINT chk_quiz_game_progress_counts CHECK (visits >= 0 AND correct_answers >= 0 AND xp_awarded >= 0),
    CONSTRAINT chk_quiz_game_progress_explored CHECK ((level = 0) = (explored_at IS NULL))
);

CREATE INDEX idx_quiz_game_progress_level
    ON cl_api_schema.quiz_game_progress (user_id, level);

COMMENT ON TABLE cl_api_schema.quiz_game_progress IS
    'One row per player and visited country; level counts the finished visits (1 explored, up to the country''s levels = mastered) and decides how the country is painted on the player''s globe.';

CREATE TABLE cl_api_schema.quiz_game_visit (
    user_id         BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    stop_number     BIGINT      NOT NULL,
    quiz_country_id BIGINT      NOT NULL REFERENCES cl_schema.quiz_game_country (id),
    level           SMALLINT    NOT NULL,
    from_country_id BIGINT      REFERENCES cl_schema.quiz_game_country (id),
    arrival_mode    VARCHAR(5),
    route           TEXT[]      NOT NULL DEFAULT '{}',
    visited_at      TIMESTAMPTZ NOT NULL DEFAULT CLOCK_TIMESTAMP(),
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (user_id, stop_number),
    CONSTRAINT chk_quiz_game_visit_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT chk_quiz_game_visit_mode CHECK (arrival_mode IS NULL OR arrival_mode IN ('DRIVE', 'FLY'))
);

COMMENT ON TABLE cl_api_schema.quiz_game_visit IS
    'Every stop a player travelled to, in order, for the route drawn on the globe.';

CREATE TABLE cl_api_schema.quiz_game_answer (
    user_id         BIGINT      NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    question_id     BIGINT      NOT NULL REFERENCES cl_schema.quiz_game_question (id),
    quiz_country_id BIGINT      NOT NULL REFERENCES cl_schema.quiz_game_country (id),
    level           SMALLINT    NOT NULL,
    selected_index  SMALLINT    NOT NULL,
    correct         BOOLEAN     NOT NULL,
    xp_awarded      INTEGER     NOT NULL DEFAULT 0,
    answered_at     TIMESTAMPTZ NOT NULL DEFAULT CLOCK_TIMESTAMP(),
    PRIMARY KEY (user_id, question_id),
    CONSTRAINT chk_quiz_game_answer_values CHECK (selected_index >= 0 AND xp_awarded >= 0 AND level BETWEEN 1 AND 3)
);

CREATE INDEX idx_quiz_game_answer_question
    ON cl_api_schema.quiz_game_answer (question_id);

COMMENT ON TABLE cl_api_schema.quiz_game_answer IS
    'Every quiz question a player answered; a question is answered at most once and earns XP once.';

CREATE VIEW cl_api_schema.user_quiz_stats AS
SELECT player.user_id,
       COALESCE(progress.explored_countries, 0) AS explored_countries,
       COALESCE(progress.mastered_countries, 0) AS mastered_countries,
       COALESCE(progress.completed_levels, 0) AS completed_levels,
       COALESCE(answers.answered_questions, 0) AS answered_questions,
       COALESCE(answers.correct_answers, 0) AS correct_answers
FROM (
    SELECT user_id FROM cl_api_schema.quiz_game_progress
    UNION
    SELECT user_id FROM cl_api_schema.quiz_game_answer
) player
LEFT JOIN (
    SELECT progress.user_id,
           COUNT(*) FILTER (WHERE progress.level >= 1) AS explored_countries,
           COUNT(*) FILTER (WHERE progress.mastered_at IS NOT NULL) AS mastered_countries,
           SUM(progress.level) AS completed_levels
    FROM cl_api_schema.quiz_game_progress progress
    GROUP BY progress.user_id
) progress ON progress.user_id = player.user_id
LEFT JOIN (
    SELECT user_id, COUNT(*) AS answered_questions, COUNT(*) FILTER (WHERE correct) AS correct_answers
    FROM cl_api_schema.quiz_game_answer
    GROUP BY user_id
) answers ON answers.user_id = player.user_id;

COMMENT ON VIEW cl_api_schema.user_quiz_stats IS
    'Per-player world tour counts: countries explored (first level done), mastered (every level done) and the levels finished in total.';

INSERT INTO cl_api_schema.app_settings (key, value)
VALUES ('quiz_game_answer_xp', '50')
ON CONFLICT (key) DO NOTHING;

CREATE SEQUENCE cl_api_schema.quiz_game_xp_event_id_seq
    START WITH -6000000000000000000 INCREMENT BY -1
    MINVALUE -6999999999999999999 MAXVALUE -6000000000000000000;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_distance_km(
    p_from_longitude DOUBLE PRECISION,
    p_from_latitude DOUBLE PRECISION,
    p_to_longitude DOUBLE PRECISION,
    p_to_latitude DOUBLE PRECISION
)
RETURNS DOUBLE PRECISION
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT 6371.0088 * 2 * ASIN(LEAST(1, SQRT(
        POWER(SIN(RADIANS(p_to_latitude - p_from_latitude) / 2), 2)
        + COS(RADIANS(p_from_latitude)) * COS(RADIANS(p_to_latitude))
            * POWER(SIN(RADIANS(p_to_longitude - p_from_longitude) / 2), 2))))
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_effective_difficulty(p_tier INTEGER, p_level INTEGER)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT p_tier + 2 * (p_level - 1)
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_pick_questions(
    p_user_id BIGINT,
    p_country_id BIGINT,
    p_level INTEGER,
    p_avoid_kinds TEXT[]
)
RETURNS BIGINT[]
LANGUAGE plpgsql
AS $$
DECLARE
    v_first BIGINT;
    v_first_kind TEXT;
    v_second BIGINT;
BEGIN
    SELECT question.id, question.kind INTO v_first, v_first_kind
    FROM cl_schema.quiz_game_question question
    WHERE question.quiz_country_id = p_country_id AND question.is_active AND question.level = p_level
    ORDER BY EXISTS (SELECT 1 FROM cl_api_schema.quiz_game_answer answer
                     WHERE answer.user_id = p_user_id AND answer.question_id = question.id),
             question.kind = ANY(p_avoid_kinds),
             random()
    LIMIT 1;

    SELECT question.id INTO v_second
    FROM cl_schema.quiz_game_question question
    WHERE question.quiz_country_id = p_country_id AND question.is_active AND question.level = p_level
      AND question.id IS DISTINCT FROM v_first
    ORDER BY EXISTS (SELECT 1 FROM cl_api_schema.quiz_game_answer answer
                     WHERE answer.user_id = p_user_id AND answer.question_id = question.id),
             question.kind = ANY(p_avoid_kinds),
             random()
    LIMIT 1;

    IF v_first IS NULL OR v_second IS NULL THEN
        RAISE EXCEPTION 'Quiz stop % has fewer than two questions on level %', p_country_id, p_level USING ERRCODE = 'CT409';
    END IF;
    RETURN ARRAY[v_first, v_second];
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_choose_next(p_user_id BIGINT, p_from_country_id BIGINT)
RETURNS TABLE(country_id BIGINT, level INTEGER, mode TEXT, route TEXT[])
LANGUAGE plpgsql
AS $$
DECLARE
    c_min_difficulty CONSTANT DOUBLE PRECISION := 1;
    c_max_difficulty CONSTANT DOUBLE PRECISION := 9;
    c_progress_curve CONSTANT DOUBLE PRECISION := 0.7;
    c_recent_answers CONSTANT INTEGER := 20;
    c_expected_accuracy CONSTANT DOUBLE PRECISION := 0.7;
    c_accuracy_pull CONSTANT DOUBLE PRECISION := 4;
    c_easier_decay CONSTANT DOUBLE PRECISION := 0.3;
    c_harder_decay CONSTANT DOUBLE PRECISION := 1.0;
    c_distance_scale_km CONSTANT DOUBLE PRECISION := 1500;
    c_drive_bonus CONSTANT DOUBLE PRECISION := 4;
    c_neighbour_km CONSTANT DOUBLE PRECISION := 3000;
    c_drive_km CONSTANT DOUBLE PRECISION := 2000;
    c_drive_hops CONSTANT INTEGER := 4;
    v_from cl_schema.quiz_game_country%ROWTYPE;
    v_completed DOUBLE PRECISION;
    v_total DOUBLE PRECISION;
    v_accuracy DOUBLE PRECISION;
    v_target DOUBLE PRECISION;
BEGIN
    SELECT * INTO v_from FROM cl_schema.quiz_game_country WHERE id = p_from_country_id;

    SELECT COALESCE(SUM(progress.level), 0) INTO v_completed
    FROM cl_api_schema.quiz_game_progress progress
    WHERE progress.user_id = p_user_id;
    SELECT GREATEST(COALESCE(SUM(country.levels), 0), 1) INTO v_total
    FROM cl_schema.quiz_game_country country
    WHERE country.is_active;
    SELECT COALESCE(AVG(CASE WHEN recent.correct THEN 1.0 ELSE 0.0 END), c_expected_accuracy) INTO v_accuracy
    FROM (
        SELECT answer.correct
        FROM cl_api_schema.quiz_game_answer answer
        WHERE answer.user_id = p_user_id
        ORDER BY answer.answered_at DESC
        LIMIT c_recent_answers
    ) recent;
    v_target := LEAST(c_max_difficulty, GREATEST(c_min_difficulty,
        c_min_difficulty + (c_max_difficulty - c_min_difficulty) * POWER(v_completed / v_total, c_progress_curve)
            + (v_accuracy - c_expected_accuracy) * c_accuracy_pull));

    RETURN QUERY
    WITH RECURSIVE land(iso2, hops, path, km) AS (
        SELECT v_from.iso2::TEXT, 0, ARRAY[v_from.iso2::TEXT], 0::DOUBLE PRECISION
        WHERE v_from.id IS NOT NULL
        UNION ALL
        SELECT neighbour.iso2::TEXT, land.hops + 1, land.path || neighbour.iso2::TEXT,
               land.km + cl_api_schema.quiz_game_distance_km(origin.longitude, origin.latitude,
                   neighbour.longitude, neighbour.latitude)
        FROM land
        JOIN cl_schema.quiz_game_country origin ON origin.iso2 = land.iso2
        JOIN cl_schema.quiz_game_country neighbour
            ON neighbour.iso2 = ANY(origin.neighbours) AND neighbour.is_active
        WHERE land.hops < c_drive_hops
          AND NOT (neighbour.iso2 = ANY(land.path))
    ), road AS (
        SELECT DISTINCT ON (land.iso2) land.iso2, land.hops, land.path
        FROM land
        ORDER BY land.iso2, land.hops, land.km
    ), candidate AS (
        SELECT country.id,
               COALESCE(progress.level, 0) + 1 AS next_level,
               cl_api_schema.quiz_game_effective_difficulty(country.tier, COALESCE(progress.level, 0) + 1) AS difficulty,
               road.path,
               CASE WHEN v_from.id IS NULL THEN 0
                    ELSE cl_api_schema.quiz_game_distance_km(v_from.longitude, v_from.latitude,
                        country.longitude, country.latitude) END AS km,
               road.hops
        FROM cl_schema.quiz_game_country country
        LEFT JOIN road ON road.iso2 = country.iso2
        LEFT JOIN cl_api_schema.quiz_game_progress progress
            ON progress.user_id = p_user_id AND progress.quiz_country_id = country.id
        WHERE country.is_active
          AND country.id IS DISTINCT FROM v_from.id
          AND COALESCE(progress.level, 0) < country.levels
    ), weighted AS (
        SELECT candidate.*,
               candidate.hops IS NOT NULL
                   AND candidate.km <= CASE WHEN candidate.hops = 1 THEN c_neighbour_km ELSE c_drive_km END AS drivable,
               CASE WHEN candidate.difficulty <= v_target
                    THEN EXP(-(v_target - candidate.difficulty) * c_easier_decay)
                    ELSE EXP(-(candidate.difficulty - v_target) * c_harder_decay) END
                   / (1 + POWER(candidate.km / c_distance_scale_km, 2)) AS weight
        FROM candidate
    )
    SELECT weighted.id, weighted.next_level,
           CASE WHEN weighted.drivable THEN 'DRIVE' ELSE 'FLY' END,
           CASE WHEN weighted.drivable THEN weighted.path[2:cardinality(weighted.path) - 1] ELSE '{}'::TEXT[] END
    FROM weighted
    ORDER BY -LN(1 - random()) / (weighted.weight * CASE WHEN weighted.drivable THEN c_drive_bonus ELSE 1 END)
    LIMIT 1;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_arrive(
    p_user_id BIGINT,
    p_country_id BIGINT,
    p_level INTEGER,
    p_mode TEXT,
    p_from_country_id BIGINT,
    p_route TEXT[]
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_avoid TEXT[];
    v_stop BIGINT;
BEGIN
    SELECT COALESCE(ARRAY_AGG(question.kind), '{}') INTO v_avoid
    FROM cl_api_schema.quiz_game_player_state state
    JOIN cl_schema.quiz_game_question question ON question.id = ANY(state.question_ids)
    WHERE state.user_id = p_user_id;

    UPDATE cl_api_schema.quiz_game_player_state
    SET stop_number = stop_number + 1,
        current_country_id = p_country_id,
        current_level = p_level,
        from_country_id = p_from_country_id,
        arrival_mode = p_mode,
        question_ids = cl_api_schema.quiz_game_pick_questions(p_user_id, p_country_id, p_level, v_avoid),
        next_country_id = NULL,
        next_level = NULL,
        next_mode = NULL,
        next_route = '{}',
        updated_at = NOW()
    WHERE user_id = p_user_id
    RETURNING stop_number INTO v_stop;

    INSERT INTO cl_api_schema.quiz_game_visit (user_id, stop_number, quiz_country_id, level, from_country_id, arrival_mode, route)
    VALUES (p_user_id, v_stop, p_country_id, p_level, p_from_country_id, p_mode, p_route);

    INSERT INTO cl_api_schema.quiz_game_progress (user_id, quiz_country_id, visits)
    VALUES (p_user_id, p_country_id, 1)
    ON CONFLICT (user_id, quiz_country_id) DO UPDATE SET
        visits = quiz_game_progress.visits + 1,
        last_visit_at = CLOCK_TIMESTAMP();

    RETURN v_stop;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_start(p_user_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.quiz_game_player_state%ROWTYPE;
    v_next RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cl_api_schema.users WHERE id = p_user_id AND enabled) THEN
        RAISE EXCEPTION 'Quiz player is unavailable' USING ERRCODE = 'CT409';
    END IF;

    INSERT INTO cl_api_schema.quiz_game_player_state (user_id)
    VALUES (p_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT * INTO v_state
    FROM cl_api_schema.quiz_game_player_state
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_state.current_country_id IS NOT NULL THEN
        RETURN v_state.stop_number;
    END IF;

    SELECT * INTO v_next FROM cl_api_schema.quiz_game_choose_next(p_user_id, NULL);
    IF v_next.country_id IS NULL THEN
        RETURN v_state.stop_number;
    END IF;
    RETURN cl_api_schema.quiz_game_arrive(p_user_id, v_next.country_id, v_next.level, NULL, NULL, '{}');
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_answer(
    p_user_id BIGINT,
    p_stop BIGINT,
    p_position INTEGER,
    p_option INTEGER
)
RETURNS TABLE(correct BOOLEAN, answer_index INTEGER, xp_awarded INTEGER, explored BOOLEAN)
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.quiz_game_player_state%ROWTYPE;
    v_question cl_schema.quiz_game_question%ROWTYPE;
    v_existing cl_api_schema.quiz_game_answer%ROWTYPE;
    v_answered INTEGER;
    v_correct BOOLEAN;
    v_xp INTEGER := 0;
    v_base_xp INTEGER;
    v_level_divisor INTEGER;
    v_finished BOOLEAN := FALSE;
    v_next RECORD;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.quiz_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_country_id IS NULL OR v_state.stop_number <> p_stop THEN
        RAISE EXCEPTION 'Quiz stop is no longer current' USING ERRCODE = 'CT409';
    END IF;
    IF p_position < 1 OR p_position > cardinality(v_state.question_ids) THEN
        RAISE EXCEPTION 'Unknown quiz question' USING ERRCODE = 'CT400';
    END IF;

    SELECT * INTO v_question FROM cl_schema.quiz_game_question WHERE id = v_state.question_ids[p_position];
    SELECT * INTO v_existing FROM cl_api_schema.quiz_game_answer
    WHERE user_id = p_user_id AND question_id = v_question.id
      AND answered_at >= (SELECT visit.visited_at FROM cl_api_schema.quiz_game_visit visit
                          WHERE visit.user_id = p_user_id AND visit.stop_number = v_state.stop_number);
    IF FOUND THEN
        RETURN QUERY SELECT v_existing.correct, v_question.answer_index::INTEGER, v_existing.xp_awarded,
            v_state.next_country_id IS NOT NULL OR p_position = cardinality(v_state.question_ids);
        RETURN;
    END IF;

    SELECT COUNT(*)::INTEGER INTO v_answered
    FROM cl_api_schema.quiz_game_answer answer
    WHERE answer.user_id = p_user_id AND answer.question_id = ANY(v_state.question_ids)
      AND answer.answered_at >= (SELECT visit.visited_at FROM cl_api_schema.quiz_game_visit visit
                                 WHERE visit.user_id = p_user_id AND visit.stop_number = v_state.stop_number);
    IF p_position <> v_answered + 1 THEN
        RAISE EXCEPTION 'Answer the questions of this stop in order' USING ERRCODE = 'CT409';
    END IF;
    IF p_option < 0 OR p_option >= jsonb_array_length(v_question.options) THEN
        RAISE EXCEPTION 'Choose one of the answers' USING ERRCODE = 'CT400';
    END IF;

    v_correct := p_option = v_question.answer_index;

    IF v_correct THEN
        SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'quiz_game_answer_xp'), 50),
               COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'level_divisor'), 100)
        INTO v_base_xp, v_level_divisor
        FROM cl_api_schema.app_settings
        WHERE key IN ('quiz_game_answer_xp', 'level_divisor');
        v_base_xp := v_base_xp * v_state.current_level;

        IF cl_api_schema.record_user_xp_event(
                NEXTVAL('cl_api_schema.quiz_game_xp_event_id_seq'),
                p_user_id, NULL, v_base_xp, 'QUIZ_GAME', v_question.id, NULL, NOW()) THEN
            v_xp := v_base_xp;
            INSERT INTO cl_api_schema.user_profiles (user_id) VALUES (p_user_id)
            ON CONFLICT (user_id) DO NOTHING;
            UPDATE cl_api_schema.user_profiles profile
            SET total_xp = profile.total_xp + v_xp,
                current_level = GREATEST(profile.current_level,
                    FLOOR(SQRT((profile.total_xp + v_xp)::DOUBLE PRECISION
                        / GREATEST(v_level_divisor, 1)))::INTEGER + 1),
                updated_at = NOW()
            WHERE profile.user_id = p_user_id;
        END IF;
    END IF;

    INSERT INTO cl_api_schema.quiz_game_answer AS given (
        user_id, question_id, quiz_country_id, level, selected_index, correct, xp_awarded
    )
    VALUES (p_user_id, v_question.id, v_state.current_country_id, v_state.current_level, p_option, v_correct, v_xp)
    ON CONFLICT (user_id, question_id) DO UPDATE SET
        level = EXCLUDED.level,
        selected_index = EXCLUDED.selected_index,
        correct = EXCLUDED.correct,
        xp_awarded = given.xp_awarded + EXCLUDED.xp_awarded,
        answered_at = EXCLUDED.answered_at;

    UPDATE cl_api_schema.quiz_game_progress progress
    SET correct_answers = progress.correct_answers + CASE WHEN v_correct THEN 1 ELSE 0 END,
        xp_awarded = progress.xp_awarded + v_xp
    WHERE progress.user_id = p_user_id AND progress.quiz_country_id = v_state.current_country_id;

    IF p_position = cardinality(v_state.question_ids) THEN
        v_finished := TRUE;
        UPDATE cl_api_schema.quiz_game_progress progress
        SET level = GREATEST(progress.level, v_state.current_level),
            explored_at = COALESCE(progress.explored_at, NOW()),
            mastered_at = CASE WHEN GREATEST(progress.level, v_state.current_level) >= country.levels
                THEN COALESCE(progress.mastered_at, NOW()) ELSE progress.mastered_at END
        FROM cl_schema.quiz_game_country country
        WHERE progress.user_id = p_user_id AND progress.quiz_country_id = v_state.current_country_id
          AND country.id = progress.quiz_country_id;

        UPDATE cl_api_schema.quiz_game_visit
        SET completed_at = NOW()
        WHERE user_id = p_user_id AND stop_number = v_state.stop_number;

        SELECT * INTO v_next FROM cl_api_schema.quiz_game_choose_next(p_user_id, v_state.current_country_id);
        UPDATE cl_api_schema.quiz_game_player_state
        SET next_country_id = v_next.country_id, next_level = v_next.level, next_mode = v_next.mode,
            next_route = COALESCE(v_next.route, '{}'), updated_at = NOW()
        WHERE user_id = p_user_id;
    ELSE
        UPDATE cl_api_schema.quiz_game_player_state SET updated_at = NOW() WHERE user_id = p_user_id;
    END IF;

    RETURN QUERY SELECT v_correct, v_question.answer_index::INTEGER, v_xp, v_finished;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_travel(p_user_id BIGINT, p_stop BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_state cl_api_schema.quiz_game_player_state%ROWTYPE;
BEGIN
    SELECT * INTO v_state FROM cl_api_schema.quiz_game_player_state
    WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR v_state.current_country_id IS NULL OR v_state.stop_number <> p_stop THEN
        RAISE EXCEPTION 'Quiz stop is no longer current' USING ERRCODE = 'CT409';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM cl_api_schema.quiz_game_visit
                   WHERE user_id = p_user_id AND stop_number = v_state.stop_number AND completed_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Answer both questions before travelling on' USING ERRCODE = 'CT409';
    END IF;
    IF v_state.next_country_id IS NULL THEN
        RETURN v_state.stop_number;
    END IF;
    RETURN cl_api_schema.quiz_game_arrive(p_user_id, v_state.next_country_id, v_state.next_level, v_state.next_mode,
        v_state.current_country_id, v_state.next_route);
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.quiz_game_claim_guest(
    p_guest_token TEXT,
    p_target_user_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_guest_user_id BIGINT;
    v_guest_state cl_api_schema.quiz_game_player_state%ROWTYPE;
    v_target_state cl_api_schema.quiz_game_player_state%ROWTYPE;
    v_answer cl_api_schema.quiz_game_answer%ROWTYPE;
    v_guest_xp INTEGER := 0;
    v_transferred_xp INTEGER := 0;
    v_answer_xp INTEGER;
    v_level_divisor INTEGER;
    v_stop_offset BIGINT;
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

    SELECT * INTO v_guest_state FROM cl_api_schema.quiz_game_player_state
    WHERE user_id = v_guest_user_id FOR UPDATE;
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;
    SELECT * INTO v_target_state FROM cl_api_schema.quiz_game_player_state
    WHERE user_id = p_target_user_id FOR UPDATE;

    FOR v_answer IN
        SELECT * FROM cl_api_schema.quiz_game_answer
        WHERE user_id = v_guest_user_id
        ORDER BY answered_at
        FOR UPDATE
    LOOP
        v_guest_xp := v_guest_xp + v_answer.xp_awarded;
        v_answer_xp := 0;
        IF v_answer.xp_awarded > 0 AND NOT EXISTS (
            SELECT 1 FROM cl_api_schema.quiz_game_answer target
            WHERE target.user_id = p_target_user_id AND target.question_id = v_answer.question_id
        ) AND cl_api_schema.record_user_xp_event(
            NEXTVAL('cl_api_schema.quiz_game_xp_event_id_seq'),
            p_target_user_id, NULL, v_answer.xp_awarded, 'QUIZ_GAME', v_answer.question_id, NULL,
            v_answer.answered_at) THEN
            v_answer_xp := v_answer.xp_awarded;
            v_transferred_xp := v_transferred_xp + v_answer_xp;
        END IF;

        INSERT INTO cl_api_schema.quiz_game_answer (
            user_id, question_id, quiz_country_id, level, selected_index, correct, xp_awarded, answered_at
        ) VALUES (
            p_target_user_id, v_answer.question_id, v_answer.quiz_country_id, v_answer.level, v_answer.selected_index,
            v_answer.correct, v_answer_xp, v_answer.answered_at
        ) ON CONFLICT (user_id, question_id) DO NOTHING;
    END LOOP;

    INSERT INTO cl_api_schema.quiz_game_progress (
        user_id, quiz_country_id, level, visits, first_seen_at, last_visit_at, explored_at, mastered_at, correct_answers, xp_awarded
    )
    SELECT p_target_user_id, guest.quiz_country_id, guest.level, guest.visits, guest.first_seen_at, guest.last_visit_at,
           guest.explored_at, guest.mastered_at, 0, 0
    FROM cl_api_schema.quiz_game_progress guest
    WHERE guest.user_id = v_guest_user_id
    ON CONFLICT (user_id, quiz_country_id) DO UPDATE SET
        level = GREATEST(quiz_game_progress.level, EXCLUDED.level),
        visits = quiz_game_progress.visits + EXCLUDED.visits,
        first_seen_at = LEAST(quiz_game_progress.first_seen_at, EXCLUDED.first_seen_at),
        last_visit_at = GREATEST(quiz_game_progress.last_visit_at, EXCLUDED.last_visit_at),
        explored_at = LEAST(quiz_game_progress.explored_at, EXCLUDED.explored_at),
        mastered_at = LEAST(quiz_game_progress.mastered_at, EXCLUDED.mastered_at);

    UPDATE cl_api_schema.quiz_game_progress progress
    SET correct_answers = totals.correct_answers,
        xp_awarded = totals.xp_awarded
    FROM (
        SELECT quiz_country_id,
               COUNT(*) FILTER (WHERE correct)::SMALLINT AS correct_answers,
               COALESCE(SUM(xp_awarded), 0)::INTEGER AS xp_awarded
        FROM cl_api_schema.quiz_game_answer
        WHERE user_id = p_target_user_id
        GROUP BY quiz_country_id
    ) totals
    WHERE progress.user_id = p_target_user_id AND progress.quiz_country_id = totals.quiz_country_id;

    IF v_target_state.user_id IS NULL OR v_target_state.current_country_id IS NULL THEN
        v_stop_offset := COALESCE(v_target_state.stop_number, 0);
        INSERT INTO cl_api_schema.quiz_game_visit (
            user_id, stop_number, quiz_country_id, level, from_country_id, arrival_mode, route, visited_at, completed_at
        )
        SELECT p_target_user_id, visit.stop_number + v_stop_offset, visit.quiz_country_id, visit.level,
               visit.from_country_id, visit.arrival_mode, visit.route, visit.visited_at, visit.completed_at
        FROM cl_api_schema.quiz_game_visit visit
        WHERE visit.user_id = v_guest_user_id;

        INSERT INTO cl_api_schema.quiz_game_player_state (
            user_id, stop_number, current_country_id, current_level, from_country_id, arrival_mode, question_ids,
            next_country_id, next_level, next_mode, next_route
        ) VALUES (
            p_target_user_id, v_guest_state.stop_number + v_stop_offset,
            v_guest_state.current_country_id, v_guest_state.current_level, v_guest_state.from_country_id,
            v_guest_state.arrival_mode, v_guest_state.question_ids, v_guest_state.next_country_id,
            v_guest_state.next_level, v_guest_state.next_mode, v_guest_state.next_route
        ) ON CONFLICT (user_id) DO UPDATE SET
            stop_number = EXCLUDED.stop_number,
            current_country_id = EXCLUDED.current_country_id,
            current_level = EXCLUDED.current_level,
            from_country_id = EXCLUDED.from_country_id,
            arrival_mode = EXCLUDED.arrival_mode,
            question_ids = EXCLUDED.question_ids,
            next_country_id = EXCLUDED.next_country_id,
            next_level = EXCLUDED.next_level,
            next_mode = EXCLUDED.next_mode,
            next_route = EXCLUDED.next_route,
            updated_at = NOW();
    END IF;

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
    WHERE user_id = v_guest_user_id AND source_type = 'QUIZ_GAME';
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

    DELETE FROM cl_api_schema.quiz_game_player_state WHERE user_id = v_guest_user_id;
    DELETE FROM cl_api_schema.quiz_game_visit WHERE user_id = v_guest_user_id;
    DELETE FROM cl_api_schema.quiz_game_answer WHERE user_id = v_guest_user_id;
    DELETE FROM cl_api_schema.quiz_game_progress WHERE user_id = v_guest_user_id;
    RETURN TRUE;
END;
$$;

INSERT INTO cl_api_schema.badge_categories (id, name, display_order)
VALUES (740000000000000001, 'World Quiz', 80)
ON CONFLICT (name) DO UPDATE SET display_order = EXCLUDED.display_order, updated_at = NOW();

INSERT INTO cl_api_schema.badges (
    id, slug, image_url, is_secret, is_active, grant_type, grant_config,
    display_order, accent_color_key, effect_color_key, category_id
)
VALUES
    (740000000000000101, 'quiz-30-explored', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"QUIZ_UNIQUE_EXPLORED","threshold":30}'::JSONB,
     801, 'sky-core', 'sky-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'World Quiz')),
    (740000000000000102, 'quiz-100-explored', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"QUIZ_UNIQUE_EXPLORED","threshold":100}'::JSONB,
     802, 'amber-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'World Quiz')),
    (740000000000000103, 'quiz-world-tour', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"QUIZ_TOUR_COMPLETED","threshold":1}'::JSONB,
     803, 'emerald-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'World Quiz'))
ON CONFLICT (slug) DO UPDATE SET
    is_active = TRUE, grant_type = EXCLUDED.grant_type,
    grant_config = EXCLUDED.grant_config, display_order = EXCLUDED.display_order,
    accent_color_key = EXCLUDED.accent_color_key,
    effect_color_key = EXCLUDED.effect_color_key,
    category_id = EXCLUDED.category_id, updated_at = NOW();

WITH translations(translation_id, slug, locale, name, unlocked, locked) AS (
    VALUES
    (740000000000001011, 'quiz-30-explored', 'en', 'Globetrotter', 'Explored 30 countries on the world tour.', 'Explore 30 countries on the world tour.'),
    (740000000000001012, 'quiz-30-explored', 'ru', 'Путешественник', 'Изучено 30 стран в кругосветке.', 'Изучи 30 стран в кругосветке.'),
    (740000000000001013, 'quiz-30-explored', 'cs', 'Cestovatel', 'Na cestě kolem světa prozkoumáno 30 zemí.', 'Prozkoumej na cestě kolem světa 30 zemí.'),
    (740000000000001021, 'quiz-100-explored', 'en', 'Seasoned Explorer', 'Explored 100 countries on the world tour.', 'Explore 100 countries on the world tour.'),
    (740000000000001022, 'quiz-100-explored', 'ru', 'Бывалый исследователь', 'Изучено 100 стран в кругосветке.', 'Изучи 100 стран в кругосветке.'),
    (740000000000001023, 'quiz-100-explored', 'cs', 'Zkušený objevitel', 'Na cestě kolem světa prozkoumáno 100 zemí.', 'Prozkoumej na cestě kolem světa 100 zemí.'),
    (740000000000001031, 'quiz-world-tour', 'en', 'Around the World', 'Explored every country on the world tour.', 'Explore every country on the world tour.'),
    (740000000000001032, 'quiz-world-tour', 'ru', 'Вокруг света', 'Изучены все страны мира в кругосветке.', 'Изучи все страны мира в кругосветке.'),
    (740000000000001033, 'quiz-world-tour', 'cs', 'Kolem světa', 'Na cestě kolem světa prozkoumány všechny země.', 'Prozkoumej na cestě kolem světa všechny země.')
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
