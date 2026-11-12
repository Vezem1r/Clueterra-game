SELECT question.iso2,
       question.region,
       question.subregion,
       question.names::TEXT AS names,
       (SELECT (hint.payload -> 'capitals')::TEXT
        FROM cl_schema.flag_game_hint hint
        WHERE hint.question_id = question.id AND hint.kind = 'CAPITAL') AS capitals
FROM cl_schema.flag_game_question question
JOIN cl_api_schema.flag_game_progress progress
    ON progress.question_id = question.id AND progress.user_id = :userId
WHERE question.iso2 = :iso2
  AND (progress.first_solved_at IS NOT NULL OR progress.reveal_count > 0)
