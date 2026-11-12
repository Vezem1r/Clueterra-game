SELECT
    claim_status,
    guest_user_id,
    target_user_id,
    session_id,
    session_status
FROM cl_api_schema.claim_budget_guest_progress(:guestToken, :targetUserId)
