SELECT state.round_number,
       question.tier,
       question.iso2,
       hints.hints,
       state.current_hints_used,
       state.current_attempts,
       EXISTS (
           SELECT 1 FROM cl_api_schema.user_xp_events xp
           WHERE xp.user_id = state.user_id AND xp.source_type = 'FLAG_GAME' AND xp.source_id = question.id
       ) AS already_rewarded,
       xp_settings.base_xp,
       xp_settings.hint_penalty_percent
FROM cl_api_schema.flag_game_player_state state
JOIN cl_schema.flag_game_question question ON question.id = state.current_question_id
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'position', hint.position, 'kind', hint.kind,
               'costUnits', hint.cost_units, 'payload', hint.payload
           ) ORDER BY hint.position), '[]'::jsonb)::TEXT AS hints
    FROM cl_schema.flag_game_hint hint
    WHERE hint.question_id = question.id
) hints
CROSS JOIN (
    SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'flag_game_base_xp'), 150) AS base_xp,
           COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'flag_game_hint_penalty_percent'), 8)
               AS hint_penalty_percent
    FROM cl_api_schema.app_settings
    WHERE key IN ('flag_game_base_xp', 'flag_game_hint_penalty_percent')
) xp_settings
WHERE state.user_id = :userId
