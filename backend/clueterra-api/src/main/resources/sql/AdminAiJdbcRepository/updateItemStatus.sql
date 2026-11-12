UPDATE cl_ai_schema.ai_hint_change_item
SET status = :status, decided_at = :decidedAt
WHERE id = :itemId
