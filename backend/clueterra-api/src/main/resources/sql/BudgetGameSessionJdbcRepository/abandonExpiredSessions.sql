SELECT cl_api_schema.abandon_expired_budget_sessions(
    (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
)
