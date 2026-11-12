SELECT ga.session_country_id,
       ga.attempt_number,
       ga.hints_at_guess,
       ga.is_correct,
       ga.created_at,
       c.id AS guessed_country_id,
       c.iso_alpha2,
       c.name_en,
       c.flag_emoji,
       c.flag_url,
       c.region
FROM guess_attempts ga
         JOIN game_session_countries gsc ON gsc.id = ga.session_country_id
         JOIN countries c ON c.id = ga.guessed_country_id
WHERE gsc.session_id = :sessionId
ORDER BY gsc.country_order, ga.attempt_number, ga.created_at
