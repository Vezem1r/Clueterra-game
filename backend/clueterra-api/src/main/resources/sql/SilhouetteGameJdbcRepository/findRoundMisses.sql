WITH points AS (
    SELECT attempt.guess_iso2 AS iso2,
           attempt.attempted_at,
           RADIANS(guess.center_lat) AS from_lat,
           RADIANS(answer.center_lat) AS to_lat,
           RADIANS(answer.center_lng - guess.center_lng) AS delta_lng
    FROM cl_api_schema.silhouette_game_attempt attempt
    JOIN cl_schema.silhouette_game_question answer ON answer.id = attempt.question_id
    JOIN cl_schema.silhouette_game_question guess ON guess.iso2 = attempt.guess_iso2 AND guess.id <> answer.id
    WHERE attempt.user_id = :userId AND attempt.round_number = :round AND NOT attempt.correct
)
SELECT iso2,
       ROUND(2 * 6371.0088 * ASIN(SQRT(
           POWER(SIN((to_lat - from_lat) / 2), 2) + COS(from_lat) * COS(to_lat) * POWER(SIN(delta_lng / 2), 2)
       )))::INTEGER AS distance_km,
       MOD(ROUND(DEGREES(ATAN2(
           SIN(delta_lng) * COS(to_lat),
           COS(from_lat) * SIN(to_lat) - SIN(from_lat) * COS(to_lat) * COS(delta_lng)
       )) + 360)::INTEGER, 360) AS bearing_deg
FROM points
ORDER BY attempted_at, iso2
