CREATE OR REPLACE FUNCTION aggregate_daily_analytics(p_date DATE)
    RETURNS VOID AS
$$
DECLARE
    v_base_id BIGINT := (EXTRACT(EPOCH FROM p_date + INTERVAL '12 hours')::BIGINT * 1000000);
BEGIN

    DELETE FROM game_statistics WHERE date = p_date;

    INSERT INTO game_statistics (id, date, challenge_level,
                                 total_sessions, completed_sessions, abandoned_sessions,
                                 total_guesses, correct_guesses,
                                 avg_attempts_per_country, avg_hints_per_country, avg_time_per_country_ms,
                                 unique_users, guest_users, registered_users)
    SELECT v_base_id + 100000 + ROW_NUMBER() OVER (ORDER BY gs.challenge_level),
           p_date,
           gs.challenge_level,
           COUNT(*)::INT,
           COUNT(*) FILTER (WHERE gs.status = 'COMPLETED')::INT,
           COUNT(*) FILTER (WHERE gs.status = 'ABANDONED')::INT,
           COALESCE(SUM(agg.total_guesses), 0)::INT,
           COALESCE(SUM(agg.correct_guesses), 0)::INT,
           ROUND(AVG(agg.avg_attempts), 2),
           ROUND(AVG(agg.avg_hints), 2),
           COALESCE(ROUND(AVG(EXTRACT(EPOCH FROM (gs.completed_at - gs.started_at)) * 1000)
                          FILTER (WHERE gs.completed_at IS NOT NULL)), 0)::BIGINT,
           COUNT(DISTINCT gs.user_id)::INT,
           COUNT(DISTINCT gs.user_id) FILTER (WHERE COALESCE(u.is_guest, FALSE))::INT,
           COUNT(DISTINCT gs.user_id) FILTER (WHERE NOT COALESCE(u.is_guest, FALSE))::INT
    FROM game_sessions gs
             LEFT JOIN users u ON u.id = gs.user_id
             LEFT JOIN LATERAL (
        SELECT COUNT(ga.id)                              AS total_guesses,
               COUNT(ga.id) FILTER (WHERE ga.is_correct) AS correct_guesses,
               AVG(gsc.attempts_used)                    AS avg_attempts,
               AVG(gsc.hints_revealed)                   AS avg_hints
        FROM game_session_countries gsc
                 LEFT JOIN guess_attempts ga ON ga.session_country_id = gsc.id
        WHERE gsc.session_id = gs.id
        ) agg ON TRUE
    WHERE DATE(gs.started_at) = p_date
    GROUP BY gs.challenge_level;

    DELETE FROM country_guess_statistics WHERE date = p_date;

    INSERT INTO country_guess_statistics (id, country_id, date,
                                          times_featured, times_guessed_correct, times_guessed_wrong,
                                          avg_attempts_needed, avg_hints_needed, avg_time_to_guess_ms,
                                          confused_with_json)
    SELECT v_base_id + 200000 + ROW_NUMBER() OVER (ORDER BY gsc.country_id),
           gsc.country_id,
           p_date,
           COUNT(*)::INT,
           COUNT(*) FILTER (WHERE gsc.is_guessed)::INT,
           COUNT(*) FILTER (WHERE NOT gsc.is_guessed)::INT,
           ROUND(AVG(gsc.attempts_used), 2),
           ROUND(AVG(gsc.hints_revealed), 2),
           ROUND(AVG(gsc.time_spent_ms))::BIGINT,
           (SELECT JSONB_AGG(
                           JSONB_BUILD_OBJECT(
                                   'countryId', ranked.guessed_country_id,
                                   'countryCode', ranked.iso_alpha2,
                                   'times', ranked.cnt
                           ) ORDER BY ranked.cnt DESC
                   )
            FROM (SELECT ga2.guessed_country_id,
                         c2.iso_alpha2,
                         COUNT(*) AS cnt
                  FROM guess_attempts ga2
                           JOIN game_session_countries gsc2 ON gsc2.id = ga2.session_country_id
                           JOIN cl_schema.countries c2 ON c2.id = ga2.guessed_country_id
                  WHERE gsc2.country_id = gsc.country_id
                    AND NOT ga2.is_correct
                    AND gsc2.session_id IN (SELECT id
                                            FROM game_sessions
                                            WHERE DATE(started_at) = p_date)
                  GROUP BY ga2.guessed_country_id, c2.iso_alpha2
                  ORDER BY COUNT(*) DESC
                  LIMIT 5) ranked)
    FROM game_session_countries gsc
             JOIN game_sessions gs ON gs.id = gsc.session_id
    WHERE DATE(gs.started_at) = p_date
    GROUP BY gsc.country_id;

    DELETE FROM hint_effectiveness WHERE date = p_date;

    INSERT INTO hint_effectiveness (id, hint_id, date,
                                    times_shown, times_led_to_correct, times_led_to_wrong,
                                    avg_position_when_correct)
    SELECT v_base_id + 300000 + ROW_NUMBER() OVER (ORDER BY h.hint_id),
           h.hint_id,
           p_date,
           COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos)::INT,
           COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos AND gsc.is_guessed)::INT,
           COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos AND NOT gsc.is_guessed)::INT,
           ROUND(AVG(h.hint_pos) FILTER (WHERE gsc.hints_revealed >= h.hint_pos AND gsc.is_guessed), 2)
    FROM (SELECT dc.id                               AS challenge_id,
                 (hint_elem ->> 'hintId')::BIGINT    AS hint_id,
                 (hint_elem ->> 'countryId')::BIGINT AS country_id,
                 hint_elem ->> 'category'            AS category,
                 ROW_NUMBER() OVER (
                     PARTITION BY dc.id, (hint_elem ->> 'countryId')::BIGINT
                     ORDER BY hint_ord.ordinality
                     )::INT                          AS hint_pos
          FROM cl_schema.daily_challenges dc,
               LATERAL JSONB_ARRAY_ELEMENTS(dc.hints_json::JSONB) WITH ORDINALITY AS hint_ord(hint_elem, ordinality)
          WHERE dc.challenge_date = p_date) h
             JOIN game_sessions gs ON gs.challenge_id = h.challenge_id
             JOIN game_session_countries gsc ON gsc.session_id = gs.id AND gsc.country_id = h.country_id
    WHERE DATE(gs.started_at) = p_date
    GROUP BY h.hint_id;

    DELETE FROM category_statistics WHERE date = p_date;

    INSERT INTO category_statistics (id, category, date,
                                     hints_shown, correct_after_hint,
                                     effectiveness_rate, avg_difficulty_shown)
    SELECT v_base_id + 400000 + ROW_NUMBER() OVER (ORDER BY h.category),
           h.category,
           p_date,
           COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos)::INT,
           COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos AND gsc.is_guessed)::INT,
           CASE
               WHEN COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos) > 0
                   THEN ROUND(
                               COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos AND gsc.is_guessed)::NUMERIC
                           / COUNT(*) FILTER (WHERE gsc.hints_revealed >= h.hint_pos) * 100, 2
                        )
               ELSE 0
               END,
           ROUND(AVG((h.hint_elem ->> 'difficulty')::NUMERIC) FILTER (WHERE gsc.hints_revealed >= h.hint_pos), 2)
    FROM (SELECT dc.id                               AS challenge_id,
                 hint_elem ->> 'category'            AS category,
                 (hint_elem ->> 'countryId')::BIGINT AS country_id,
                 hint_elem,
                 ROW_NUMBER() OVER (
                     PARTITION BY dc.id, (hint_elem ->> 'countryId')::BIGINT
                     ORDER BY hint_ord.ordinality
                     )::INT                          AS hint_pos
          FROM cl_schema.daily_challenges dc,
               LATERAL JSONB_ARRAY_ELEMENTS(dc.hints_json::JSONB) WITH ORDINALITY AS hint_ord(hint_elem, ordinality)
          WHERE dc.challenge_date = p_date) h
             JOIN game_sessions gs ON gs.challenge_id = h.challenge_id
             JOIN game_session_countries gsc ON gsc.session_id = gs.id AND gsc.country_id = h.country_id
    WHERE DATE(gs.started_at) = p_date
    GROUP BY h.category;

END;
$$ LANGUAGE plpgsql;
