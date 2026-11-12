SELECT q.id, q.iso2, q.names ->> 'en' AS name_en, q.tier, q.region, q.subregion, q.is_active, q.generated_at,
       hints.hints,
       stats.players, stats.solved, stats.skips, stats.reveals, stats.average_hints,
       misses.wrong_guesses
FROM cl_schema.flag_game_question q
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'position', h.position, 'kind', h.kind, 'costUnits', h.cost_units, 'payload', h.payload
           ) ORDER BY h.position), '[]'::jsonb)::TEXT AS hints
    FROM cl_schema.flag_game_hint h
    WHERE h.question_id = q.id
) hints
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS players,
           COUNT(*) FILTER (WHERE progress.first_solved_at IS NOT NULL) AS solved,
           COALESCE(SUM(progress.skip_count), 0) AS skips,
           COALESCE(SUM(progress.reveal_count), 0) AS reveals,
           AVG(progress.best_hints_used) AS average_hints
    FROM cl_api_schema.flag_game_progress progress
    WHERE progress.question_id = q.id
) stats
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS wrong_guesses
    FROM cl_api_schema.flag_game_attempt attempt
    WHERE attempt.question_id = q.id AND NOT attempt.correct
) misses
