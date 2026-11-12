WITH expired_guests AS (
    SELECT id
    FROM users
    WHERE is_guest = TRUE
      AND guest_token_expires_at < NOW()
),
deleted_legal_events AS (
    DELETE FROM legal_acceptance_events
    WHERE user_id IN (SELECT id FROM expired_guests)
       OR game_session_id IN (
           SELECT id FROM game_sessions WHERE user_id IN (SELECT id FROM expired_guests)
       )
    RETURNING id
),
deleted_sessions AS (
    DELETE FROM game_sessions
    WHERE user_id IN (SELECT id FROM expired_guests)
    RETURNING user_id
),
deleted_users AS (
    DELETE FROM users
    WHERE id IN (SELECT id FROM expired_guests)
      AND (SELECT COUNT(*) FROM deleted_legal_events) >= 0
      AND (SELECT COUNT(*) FROM deleted_sessions) >= 0
    RETURNING id
)
SELECT COUNT(*) FROM deleted_users
