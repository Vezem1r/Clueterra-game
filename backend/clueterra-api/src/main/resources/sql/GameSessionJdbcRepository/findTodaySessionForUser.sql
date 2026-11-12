SELECT gs.id  AS session_id,
       gs.user_id,
       gs.guest_token,
       gs.challenge_id,
       dc.challenge_date,
       gs.challenge_level,
       gs.status,
       gs.current_country_idx,
       gs.started_at,
       gs.last_activity_at,
       gs.completed_at,
       gs.total_xp_earned,
       gs.max_hints_per_country,
       gs.max_attempts,
       gs.xp_before,
       gs.level_before,
       gs.terms_version_accepted,
       gs.privacy_version_accepted,
       CASE
           WHEN ROW_NUMBER() OVER (PARTITION BY gs.id ORDER BY gsc.country_order) = 1
               THEN dc.hints_json
       END AS hints_json,
       gsc.id AS sc_id,
       gsc.country_id,
       gsc.country_order,
       gsc.total_hints,
       gsc.hints_revealed,
       gsc.attempts_used,
       gsc.is_guessed,
       gsc.guessed_at,
       gsc.time_spent_ms,
       gsc.xp_earned,
       c.iso_alpha2,
       c.name_en,
       c.flag_emoji,
       c.flag_url,
       c.region
FROM user_daily_participation udp
         JOIN game_sessions gs ON gs.id = udp.session_id
         JOIN daily_challenges dc ON dc.id = gs.challenge_id
         JOIN game_session_countries gsc ON gsc.session_id = gs.id
         JOIN countries c ON c.id = gsc.country_id
WHERE udp.user_id = :userId
  AND udp.challenge_date = :today
ORDER BY gsc.country_order
