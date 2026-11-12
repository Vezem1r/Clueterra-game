SELECT attempt.guess_iso2
FROM cl_api_schema.flag_game_attempt attempt
WHERE attempt.user_id = :userId AND attempt.round_number = :round AND NOT attempt.correct
ORDER BY attempt.attempted_at, attempt.guess_iso2
