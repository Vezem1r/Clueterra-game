SELECT app_user.guest_token
FROM cl_api_schema.budget_game_session session
JOIN cl_api_schema.users app_user ON app_user.id = session.user_id
WHERE session.id = :sessionId
    AND app_user.is_guest
    AND app_user.guest_token_expires_at > NOW()
