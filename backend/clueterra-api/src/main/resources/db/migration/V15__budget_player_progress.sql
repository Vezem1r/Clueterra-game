CREATE TABLE cl_api_schema.user_budget_stats (
    user_id                     BIGINT                   PRIMARY KEY REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    completed_challenges        INTEGER                  NOT NULL DEFAULT 0,
    answered_questions          INTEGER                  NOT NULL DEFAULT 0,
    full_xp_answers             INTEGER                  NOT NULL DEFAULT 0,
    exact_guesses               INTEGER                  NOT NULL DEFAULT 0,
    clean_sweeps                INTEGER                  NOT NULL DEFAULT 0,
    budget_xp_earned            BIGINT                   NOT NULL DEFAULT 0,
    accuracy_total              NUMERIC(20, 2)           NOT NULL DEFAULT 0,
    higher_lower_attempts       INTEGER                  NOT NULL DEFAULT 0,
    higher_lower_correct        INTEGER                  NOT NULL DEFAULT 0,
    higher_lower_current_streak INTEGER                  NOT NULL DEFAULT 0,
    higher_lower_best_streak    INTEGER                  NOT NULL DEFAULT 0,
    cost_per_unit_correct       INTEGER                  NOT NULL DEFAULT 0,
    severe_misses               INTEGER                  NOT NULL DEFAULT 0,
    unique_projects_explored    INTEGER                  NOT NULL DEFAULT 0,
    unique_projects_mastered    INTEGER                  NOT NULL DEFAULT 0,
    categories_mastered         INTEGER                  NOT NULL DEFAULT 0,
    countries_mastered          INTEGER                  NOT NULL DEFAULT 0,
    regions_mastered            INTEGER                  NOT NULL DEFAULT 0,
    mastered_project_value_usd  NUMERIC(30, 2)           NOT NULL DEFAULT 0,
    last_completed_at           TIMESTAMP WITH TIME ZONE,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_budget_stats_nonnegative CHECK (
        completed_challenges >= 0
        AND answered_questions >= 0
        AND full_xp_answers >= 0
        AND exact_guesses >= 0
        AND clean_sweeps >= 0
        AND budget_xp_earned >= 0
        AND accuracy_total >= 0
        AND higher_lower_attempts >= 0
        AND higher_lower_correct >= 0
        AND higher_lower_current_streak >= 0
        AND higher_lower_best_streak >= 0
        AND cost_per_unit_correct >= 0
        AND severe_misses >= 0
        AND unique_projects_explored >= 0
        AND unique_projects_mastered >= 0
        AND categories_mastered >= 0
        AND countries_mastered >= 0
        AND regions_mastered >= 0
        AND mastered_project_value_usd >= 0
    )
);

CREATE TABLE cl_api_schema.user_budget_project_progress (
    user_id           BIGINT                   NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    project_id        BIGINT                   NOT NULL REFERENCES cl_budget_schema.project (id) ON DELETE CASCADE,
    category          VARCHAR(30)              NOT NULL,
    country_code      CHAR(2)                  NOT NULL,
    region_name       VARCHAR(120)             NOT NULL,
    project_cost_usd  NUMERIC(20, 2)           NOT NULL,
    first_explored_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_mastered_at TIMESTAMP WITH TIME ZONE,
    source_answer_id  BIGINT                   REFERENCES cl_api_schema.budget_game_answer (id) ON DELETE SET NULL,
    mastery_answer_id BIGINT                   REFERENCES cl_api_schema.budget_game_answer (id) ON DELETE SET NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, project_id),
    CONSTRAINT chk_user_budget_project_progress_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_user_budget_project_progress_cost CHECK (project_cost_usd > 0),
    CONSTRAINT chk_user_budget_project_progress_mastery CHECK (
        (first_mastered_at IS NULL AND mastery_answer_id IS NULL)
        OR (first_mastered_at IS NOT NULL AND mastery_answer_id IS NOT NULL)
    ),
    CONSTRAINT chk_user_budget_project_progress_category CHECK (
        category IN ('BRIDGE', 'METRO_RAIL', 'STADIUM', 'SKYSCRAPER', 'AIRPORT',
                     'TUNNEL', 'DAM', 'ENERGY', 'SPACE', 'ROAD_HIGHWAY', 'PORT')
    )
);

CREATE INDEX idx_user_budget_project_progress_mastered
ON cl_api_schema.user_budget_project_progress (user_id, first_mastered_at)
WHERE first_mastered_at IS NOT NULL;

CREATE OR REPLACE FUNCTION cl_api_schema.refresh_user_budget_project_totals (
    p_user_id BIGINT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE cl_api_schema.user_budget_stats stats
    SET
        unique_projects_explored = project_stats.explored,
        unique_projects_mastered = project_stats.mastered,
        categories_mastered = project_stats.categories,
        countries_mastered = project_stats.countries,
        regions_mastered = project_stats.regions,
        mastered_project_value_usd = project_stats.mastered_value,
        updated_at = NOW()
    FROM (
        SELECT
            COUNT(*)::INTEGER AS explored,
            COUNT(*) FILTER (WHERE progress.first_mastered_at IS NOT NULL)::INTEGER AS mastered,
            COUNT(DISTINCT progress.category) FILTER (
                WHERE progress.first_mastered_at IS NOT NULL
            )::INTEGER AS categories,
            COUNT(DISTINCT progress.country_code) FILTER (
                WHERE progress.first_mastered_at IS NOT NULL
            )::INTEGER AS countries,
            COUNT(DISTINCT progress.region_name) FILTER (
                WHERE progress.first_mastered_at IS NOT NULL
            )::INTEGER AS regions,
            COALESCE(SUM(progress.project_cost_usd) FILTER (
                WHERE progress.first_mastered_at IS NOT NULL
            ), 0)::NUMERIC(30, 2) AS mastered_value
        FROM cl_api_schema.user_budget_project_progress progress
        WHERE progress.user_id = p_user_id
    ) project_stats
    WHERE stats.user_id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.apply_budget_answer_projection (
    p_answer_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id BIGINT;
    v_question_type TEXT;
    v_accuracy_percent NUMERIC(5, 2);
    v_xp_percent NUMERIC(5, 2);
    v_xp_earned INTEGER;
    v_answered_at TIMESTAMP WITH TIME ZONE;
    v_projection_applied_at TIMESTAMP WITH TIME ZONE;
BEGIN
    SELECT
        session.user_id,
        answer.question_type,
        answer.accuracy_percent,
        answer.xp_percent,
        answer.xp_earned,
        answer.answered_at,
        answer.profile_projection_applied_at
    INTO
        v_user_id,
        v_question_type,
        v_accuracy_percent,
        v_xp_percent,
        v_xp_earned,
        v_answered_at,
        v_projection_applied_at
    FROM cl_api_schema.budget_game_answer answer
    JOIN cl_api_schema.budget_game_session session ON session.id = answer.session_id
    WHERE answer.id = p_answer_id
    FOR UPDATE OF answer;

    IF v_user_id IS NULL OR v_projection_applied_at IS NOT NULL THEN
        RETURN FALSE;
    END IF;

    INSERT INTO cl_api_schema.user_budget_stats (user_id)
    VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO cl_api_schema.user_budget_project_progress (
        user_id,
        project_id,
        category,
        country_code,
        region_name,
        project_cost_usd,
        first_explored_at,
        first_mastered_at,
        source_answer_id,
        mastery_answer_id
    )
    SELECT
        v_user_id,
        referenced.project_id,
        referenced.category,
        referenced.country_code,
        referenced.region_name,
        referenced.answer_cost_usd,
        v_answered_at,
        CASE WHEN v_xp_percent = 100 THEN v_answered_at END,
        p_answer_id,
        CASE WHEN v_xp_percent = 100 THEN p_answer_id END
    FROM (
        SELECT
            source_question.project_id,
            source_question.category,
            source_question.country_code,
            source_question.region_name,
            source_question.answer_cost_usd
        FROM cl_api_schema.budget_game_answer answer
        JOIN cl_budget_schema.question_variant variant
            ON variant.id = answer.question_variant_id
        JOIN cl_budget_schema.question source_question
            ON source_question.id = variant.source_question_id
        WHERE answer.id = p_answer_id
        UNION ALL
        SELECT
            comparison_question.project_id,
            comparison_question.category,
            comparison_question.country_code,
            comparison_question.region_name,
            comparison_question.answer_cost_usd
        FROM cl_api_schema.budget_game_answer answer
        JOIN cl_budget_schema.question_variant variant
            ON variant.id = answer.question_variant_id
        JOIN cl_budget_schema.question comparison_question
            ON comparison_question.id = variant.comparison_question_id
        WHERE answer.id = p_answer_id
    ) referenced
    ON CONFLICT (user_id, project_id) DO UPDATE SET
        first_explored_at = LEAST(
            cl_api_schema.user_budget_project_progress.first_explored_at,
            EXCLUDED.first_explored_at
        ),
        first_mastered_at = COALESCE(
            cl_api_schema.user_budget_project_progress.first_mastered_at,
            EXCLUDED.first_mastered_at
        ),
        mastery_answer_id = COALESCE(
            cl_api_schema.user_budget_project_progress.mastery_answer_id,
            EXCLUDED.mastery_answer_id
        ),
        project_cost_usd = CASE
            WHEN cl_api_schema.user_budget_project_progress.first_mastered_at IS NULL
                AND EXCLUDED.first_mastered_at IS NOT NULL
                THEN EXCLUDED.project_cost_usd
            ELSE cl_api_schema.user_budget_project_progress.project_cost_usd
        END,
        updated_at = NOW();

    UPDATE cl_api_schema.user_budget_stats stats
    SET
        answered_questions = stats.answered_questions + 1,
        full_xp_answers = stats.full_xp_answers + CASE WHEN v_xp_percent = 100 THEN 1 ELSE 0 END,
        exact_guesses = stats.exact_guesses + CASE
            WHEN v_question_type <> 'HIGHER_LOWER' AND v_accuracy_percent = 100 THEN 1 ELSE 0 END,
        budget_xp_earned = stats.budget_xp_earned + v_xp_earned,
        accuracy_total = stats.accuracy_total + v_accuracy_percent,
        higher_lower_attempts = stats.higher_lower_attempts + CASE
            WHEN v_question_type = 'HIGHER_LOWER' THEN 1 ELSE 0 END,
        higher_lower_correct = stats.higher_lower_correct + CASE
            WHEN v_question_type = 'HIGHER_LOWER' AND v_xp_percent = 100 THEN 1 ELSE 0 END,
        higher_lower_current_streak = CASE
            WHEN v_question_type <> 'HIGHER_LOWER' THEN stats.higher_lower_current_streak
            WHEN v_xp_percent = 100 THEN stats.higher_lower_current_streak + 1
            ELSE 0
        END,
        higher_lower_best_streak = CASE
            WHEN v_question_type = 'HIGHER_LOWER' AND v_xp_percent = 100
                THEN GREATEST(stats.higher_lower_best_streak, stats.higher_lower_current_streak + 1)
            ELSE stats.higher_lower_best_streak
        END,
        cost_per_unit_correct = stats.cost_per_unit_correct + CASE
            WHEN v_question_type = 'COST_PER_UNIT' AND v_xp_percent = 100 THEN 1 ELSE 0 END,
        severe_misses = stats.severe_misses + CASE
            WHEN v_question_type <> 'HIGHER_LOWER' AND v_accuracy_percent <= 5 THEN 1 ELSE 0 END,
        updated_at = NOW()
    WHERE stats.user_id = v_user_id;

    PERFORM cl_api_schema.refresh_user_budget_project_totals(v_user_id);

    UPDATE cl_api_schema.budget_game_answer answer
    SET profile_projection_applied_at = NOW()
    WHERE answer.id = p_answer_id
        AND answer.profile_projection_applied_at IS NULL;

    RETURN FOUND;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.apply_budget_session_projection (
    p_session_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id BIGINT;
    v_completed_at TIMESTAMP WITH TIME ZONE;
    v_projection_applied_at TIMESTAMP WITH TIME ZONE;
    v_clean_sweep BOOLEAN;
BEGIN
    SELECT
        session.user_id,
        session.completed_at,
        session.profile_projection_applied_at
    INTO
        v_user_id,
        v_completed_at,
        v_projection_applied_at
    FROM cl_api_schema.budget_game_session session
    WHERE session.id = p_session_id
        AND session.status = 'COMPLETED';

    IF v_user_id IS NULL OR v_projection_applied_at IS NOT NULL THEN
        RETURN FALSE;
    END IF;

    SELECT COUNT(*) = 5 AND COUNT(*) FILTER (WHERE answer.xp_percent = 100) = 5
    INTO v_clean_sweep
    FROM cl_api_schema.budget_game_answer answer
    WHERE answer.session_id = p_session_id;

    INSERT INTO cl_api_schema.user_budget_stats (user_id)
    VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    UPDATE cl_api_schema.user_budget_stats stats
    SET
        completed_challenges = stats.completed_challenges + 1,
        clean_sweeps = stats.clean_sweeps + CASE WHEN COALESCE(v_clean_sweep, FALSE) THEN 1 ELSE 0 END,
        last_completed_at = GREATEST(
            COALESCE(stats.last_completed_at, v_completed_at),
            v_completed_at
        ),
        updated_at = NOW()
    WHERE stats.user_id = v_user_id;

    UPDATE cl_api_schema.budget_game_session session
    SET profile_projection_applied_at = NOW()
    WHERE session.id = p_session_id
        AND session.profile_projection_applied_at IS NULL;

    RETURN FOUND;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.rebuild_user_budget_projection (
    p_user_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_higher_lower_current INTEGER := 0;
    v_higher_lower_best INTEGER := 0;
    v_answer RECORD;
BEGIN
    PERFORM 1
    FROM cl_api_schema.users app_user
    WHERE app_user.id = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    INSERT INTO cl_api_schema.user_budget_stats (user_id)
    VALUES (p_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    DELETE FROM cl_api_schema.user_budget_project_progress progress
    WHERE progress.user_id = p_user_id;

    WITH referenced_projects AS (
        SELECT
            answer.id AS answer_id,
            answer.answered_at,
            answer.xp_percent = 100 AS mastered,
            source_question.project_id,
            source_question.category,
            source_question.country_code,
            source_question.region_name,
            source_question.answer_cost_usd
        FROM cl_api_schema.budget_game_answer answer
        JOIN cl_api_schema.budget_game_session session ON session.id = answer.session_id
        JOIN cl_budget_schema.question_variant variant ON variant.id = answer.question_variant_id
        JOIN cl_budget_schema.question source_question ON source_question.id = variant.source_question_id
        WHERE session.user_id = p_user_id
        UNION ALL
        SELECT
            answer.id,
            answer.answered_at,
            answer.xp_percent = 100,
            comparison_question.project_id,
            comparison_question.category,
            comparison_question.country_code,
            comparison_question.region_name,
            comparison_question.answer_cost_usd
        FROM cl_api_schema.budget_game_answer answer
        JOIN cl_api_schema.budget_game_session session ON session.id = answer.session_id
        JOIN cl_budget_schema.question_variant variant ON variant.id = answer.question_variant_id
        JOIN cl_budget_schema.question comparison_question ON comparison_question.id = variant.comparison_question_id
        WHERE session.user_id = p_user_id
    ), aggregated AS (
        SELECT
            referenced.project_id,
            (ARRAY_AGG(referenced.category ORDER BY referenced.answered_at, referenced.answer_id))[1] AS category,
            (ARRAY_AGG(referenced.country_code ORDER BY referenced.answered_at, referenced.answer_id))[1] AS country_code,
            (ARRAY_AGG(referenced.region_name ORDER BY referenced.answered_at, referenced.answer_id))[1] AS region_name,
            COALESCE(
                (ARRAY_AGG(referenced.answer_cost_usd ORDER BY referenced.answered_at, referenced.answer_id)
                    FILTER (WHERE referenced.mastered))[1],
                (ARRAY_AGG(referenced.answer_cost_usd ORDER BY referenced.answered_at, referenced.answer_id))[1]
            ) AS project_cost_usd,
            MIN(referenced.answered_at) AS first_explored_at,
            MIN(referenced.answered_at) FILTER (WHERE referenced.mastered) AS first_mastered_at,
            (ARRAY_AGG(referenced.answer_id ORDER BY referenced.answered_at, referenced.answer_id))[1] AS source_answer_id,
            (ARRAY_AGG(referenced.answer_id ORDER BY referenced.answered_at, referenced.answer_id)
                FILTER (WHERE referenced.mastered))[1] AS mastery_answer_id
        FROM referenced_projects referenced
        GROUP BY referenced.project_id
    )
    INSERT INTO cl_api_schema.user_budget_project_progress (
        user_id,
        project_id,
        category,
        country_code,
        region_name,
        project_cost_usd,
        first_explored_at,
        first_mastered_at,
        source_answer_id,
        mastery_answer_id
    )
    SELECT
        p_user_id,
        aggregated.project_id,
        aggregated.category,
        aggregated.country_code,
        aggregated.region_name,
        aggregated.project_cost_usd,
        aggregated.first_explored_at,
        aggregated.first_mastered_at,
        aggregated.source_answer_id,
        aggregated.mastery_answer_id
    FROM aggregated;

    UPDATE cl_api_schema.user_budget_stats stats
    SET
        completed_challenges = aggregate.completed_challenges,
        answered_questions = aggregate.answered_questions,
        full_xp_answers = aggregate.full_xp_answers,
        exact_guesses = aggregate.exact_guesses,
        clean_sweeps = aggregate.clean_sweeps,
        budget_xp_earned = aggregate.budget_xp_earned,
        accuracy_total = aggregate.accuracy_total,
        higher_lower_attempts = aggregate.higher_lower_attempts,
        higher_lower_correct = aggregate.higher_lower_correct,
        higher_lower_current_streak = 0,
        higher_lower_best_streak = 0,
        cost_per_unit_correct = aggregate.cost_per_unit_correct,
        severe_misses = aggregate.severe_misses,
        last_completed_at = aggregate.last_completed_at,
        updated_at = NOW()
    FROM (
        SELECT
            COUNT(DISTINCT session.id) FILTER (WHERE session.status = 'COMPLETED')::INTEGER AS completed_challenges,
            COUNT(answer.id)::INTEGER AS answered_questions,
            COUNT(answer.id) FILTER (WHERE answer.xp_percent = 100)::INTEGER AS full_xp_answers,
            COUNT(answer.id) FILTER (
                WHERE answer.question_type <> 'HIGHER_LOWER' AND answer.accuracy_percent = 100
            )::INTEGER AS exact_guesses,
            COUNT(DISTINCT session.id) FILTER (
                WHERE session.status = 'COMPLETED'
                    AND (SELECT COUNT(*) FROM cl_api_schema.budget_game_answer sweep_answer
                         WHERE sweep_answer.session_id = session.id) = 5
                    AND NOT EXISTS (
                        SELECT 1
                        FROM cl_api_schema.budget_game_answer sweep_answer
                        WHERE sweep_answer.session_id = session.id
                            AND sweep_answer.xp_percent <> 100
                    )
            )::INTEGER AS clean_sweeps,
            COALESCE(SUM(answer.xp_earned), 0)::BIGINT AS budget_xp_earned,
            COALESCE(SUM(answer.accuracy_percent), 0)::NUMERIC(20, 2) AS accuracy_total,
            COUNT(answer.id) FILTER (WHERE answer.question_type = 'HIGHER_LOWER')::INTEGER AS higher_lower_attempts,
            COUNT(answer.id) FILTER (
                WHERE answer.question_type = 'HIGHER_LOWER' AND answer.xp_percent = 100
            )::INTEGER AS higher_lower_correct,
            COUNT(answer.id) FILTER (
                WHERE answer.question_type = 'COST_PER_UNIT' AND answer.xp_percent = 100
            )::INTEGER AS cost_per_unit_correct,
            COUNT(answer.id) FILTER (
                WHERE answer.question_type <> 'HIGHER_LOWER' AND answer.accuracy_percent <= 5
            )::INTEGER AS severe_misses,
            MAX(session.completed_at) FILTER (WHERE session.status = 'COMPLETED') AS last_completed_at
        FROM cl_api_schema.budget_game_session session
        LEFT JOIN cl_api_schema.budget_game_answer answer ON answer.session_id = session.id
        WHERE session.user_id = p_user_id
    ) aggregate
    WHERE stats.user_id = p_user_id;

    FOR v_answer IN
        SELECT answer.xp_percent
        FROM cl_api_schema.budget_game_answer answer
        JOIN cl_api_schema.budget_game_session session ON session.id = answer.session_id
        WHERE session.user_id = p_user_id
            AND answer.question_type = 'HIGHER_LOWER'
        ORDER BY answer.answered_at, answer.id
    LOOP
        IF v_answer.xp_percent = 100 THEN
            v_higher_lower_current := v_higher_lower_current + 1;
            v_higher_lower_best := GREATEST(v_higher_lower_best, v_higher_lower_current);
        ELSE
            v_higher_lower_current := 0;
        END IF;
    END LOOP;

    UPDATE cl_api_schema.user_budget_stats stats
    SET
        higher_lower_current_streak = v_higher_lower_current,
        higher_lower_best_streak = v_higher_lower_best,
        updated_at = NOW()
    WHERE stats.user_id = p_user_id;

    PERFORM cl_api_schema.refresh_user_budget_project_totals(p_user_id);

    UPDATE cl_api_schema.budget_game_answer answer
    SET profile_projection_applied_at = COALESCE(answer.profile_projection_applied_at, NOW())
    FROM cl_api_schema.budget_game_session session
    WHERE session.id = answer.session_id
        AND session.user_id = p_user_id;

    UPDATE cl_api_schema.budget_game_session session
    SET profile_projection_applied_at = COALESCE(session.profile_projection_applied_at, session.completed_at, NOW())
    WHERE session.user_id = p_user_id
        AND session.status = 'COMPLETED';

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.project_budget_answer_after_insert ()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM cl_api_schema.apply_budget_answer_projection(NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budget_answer_projection
AFTER INSERT ON cl_api_schema.budget_game_answer
FOR EACH ROW
EXECUTE FUNCTION cl_api_schema.project_budget_answer_after_insert();

CREATE OR REPLACE FUNCTION cl_api_schema.project_budget_session_after_completion ()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM cl_api_schema.apply_budget_session_projection(NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budget_session_projection
AFTER UPDATE OF status ON cl_api_schema.budget_game_session
FOR EACH ROW
WHEN (NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION cl_api_schema.project_budget_session_after_completion();

CREATE OR REPLACE FUNCTION cl_api_schema.claim_budget_guest_progress (
    p_guest_token TEXT,
    p_target_user_id BIGINT
)
RETURNS TABLE (
    claim_status TEXT,
    guest_user_id BIGINT,
    target_user_id BIGINT,
    session_id BIGINT,
    session_status TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_guest_user_id BIGINT;
    v_session_ids BIGINT[];
    v_result_session_id BIGINT;
    v_result_session_status TEXT;
    v_xp_before BIGINT;
    v_level_before INTEGER;
    v_transferred INTEGER := 0;
    v_conflicts INTEGER := 0;
    v_completed_transferred INTEGER := 0;
    v_session_id BIGINT;
BEGIN
    IF p_guest_token IS NULL OR BTRIM(p_guest_token) = '' OR p_target_user_id IS NULL THEN
        RETURN QUERY SELECT 'NO_GUEST'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    PERFORM PG_ADVISORY_XACT_LOCK(
        HASHTEXTEXTENDED('budget-guest-claim:' || p_target_user_id::TEXT, 0)
    );

    PERFORM 1
    FROM cl_api_schema.users target
    WHERE target.id = p_target_user_id
        AND target.enabled
        AND NOT target.is_guest
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN QUERY SELECT 'TARGET_NOT_FOUND'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    SELECT guest.id
    INTO v_guest_user_id
    FROM cl_api_schema.users guest
    WHERE guest.guest_token = p_guest_token
        AND guest.is_guest
        AND guest.guest_token_expires_at > NOW()
    FOR UPDATE;

    IF v_guest_user_id IS NULL THEN
        RETURN QUERY SELECT 'NO_GUEST'::TEXT, NULL::BIGINT, p_target_user_id, NULL::BIGINT, NULL::TEXT;
        RETURN;
    END IF;

    SELECT COUNT(*)::INTEGER
    INTO v_conflicts
    FROM cl_api_schema.budget_game_session guest_session
    WHERE guest_session.user_id = v_guest_user_id
        AND EXISTS (
            SELECT 1
            FROM cl_api_schema.budget_game_session target_session
            WHERE target_session.user_id = p_target_user_id
                AND target_session.challenge_id = guest_session.challenge_id
        );

    SELECT ARRAY_AGG(guest_session.id ORDER BY challenge.challenge_date, guest_session.id)
    INTO v_session_ids
    FROM cl_api_schema.budget_game_session guest_session
    JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = guest_session.challenge_id
    WHERE guest_session.user_id = v_guest_user_id
        AND NOT EXISTS (
            SELECT 1
            FROM cl_api_schema.budget_game_session target_session
            WHERE target_session.user_id = p_target_user_id
                AND target_session.challenge_id = guest_session.challenge_id
        );

    IF COALESCE(CARDINALITY(v_session_ids), 0) = 0 THEN
        RETURN QUERY SELECT
            CASE WHEN v_conflicts > 0 THEN 'TARGET_ALREADY_PLAYED' ELSE 'NO_PROGRESS' END,
            v_guest_user_id,
            p_target_user_id,
            NULL::BIGINT,
            NULL::TEXT;
        RETURN;
    END IF;

    SELECT COALESCE(profile.total_xp, 0), COALESCE(profile.current_level, 1)
    INTO v_xp_before, v_level_before
    FROM cl_api_schema.user_profiles profile
    WHERE profile.user_id = p_target_user_id;

    v_xp_before := COALESCE(v_xp_before, 0);
    v_level_before := COALESCE(v_level_before, 1);

    UPDATE cl_api_schema.budget_game_session session
    SET
        user_id = p_target_user_id,
        xp_before = CASE WHEN session.status = 'IN_PROGRESS' THEN v_xp_before ELSE session.xp_before END,
        level_before = CASE WHEN session.status = 'IN_PROGRESS' THEN v_level_before ELSE session.level_before END,
        profile_projection_applied_at = NULL,
        updated_at = NOW()
    WHERE session.id = ANY(v_session_ids);
    GET DIAGNOSTICS v_transferred = ROW_COUNT;

    UPDATE cl_api_schema.budget_game_answer answer
    SET profile_projection_applied_at = NULL
    WHERE answer.session_id = ANY(v_session_ids);

    FOREACH v_session_id IN ARRAY v_session_ids
    LOOP
        IF cl_api_schema.apply_budget_session_xp(v_session_id) THEN
            v_completed_transferred := v_completed_transferred + 1;
        END IF;
    END LOOP;

    PERFORM cl_api_schema.rebuild_user_budget_projection(p_target_user_id);
    PERFORM cl_api_schema.rebuild_user_budget_projection(v_guest_user_id);

    UPDATE cl_api_schema.legal_acceptance_events event
    SET user_id = p_target_user_id
    WHERE event.user_id = v_guest_user_id
        AND event.acceptance_source LIKE 'GUEST_%';

    SELECT session.id, session.status
    INTO v_result_session_id, v_result_session_status
    FROM cl_api_schema.budget_game_session session
    JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = session.challenge_id
    WHERE session.id = ANY(v_session_ids)
    ORDER BY
        (challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE) DESC,
        challenge.challenge_date DESC,
        session.id DESC
    LIMIT 1;

    RETURN QUERY SELECT
        CASE
            WHEN v_result_session_status = 'IN_PROGRESS' THEN 'CLAIMED_IN_PROGRESS'
            WHEN v_completed_transferred > 0 THEN 'CLAIMED_COMPLETED'
            ELSE 'CLAIMED_HISTORY'
        END,
        v_guest_user_id,
        p_target_user_id,
        v_result_session_id,
        v_result_session_status;
END;
$$;

CREATE OR REPLACE FUNCTION cl_api_schema.finalize_converted_budget_progress (
    p_user_id BIGINT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_session RECORD;
    v_applied_count INTEGER := 0;
BEGIN
    PERFORM 1
    FROM cl_api_schema.users app_user
    WHERE app_user.id = p_user_id
        AND app_user.enabled
        AND NOT app_user.is_guest
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    FOR v_session IN
        SELECT session.id
        FROM cl_api_schema.budget_game_session session
        WHERE session.user_id = p_user_id
            AND session.status = 'COMPLETED'
            AND session.xp_applied_at IS NULL
        ORDER BY session.completed_at, session.id
        FOR UPDATE
    LOOP
        IF cl_api_schema.apply_budget_session_xp(v_session.id) THEN
            v_applied_count := v_applied_count + 1;
        END IF;
    END LOOP;

    PERFORM cl_api_schema.rebuild_user_budget_projection(p_user_id);
    RETURN v_applied_count > 0;
END;
$$;
