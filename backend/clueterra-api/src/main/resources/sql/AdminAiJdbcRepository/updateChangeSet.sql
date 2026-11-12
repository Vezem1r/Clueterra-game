UPDATE cl_ai_schema.ai_hint_change_set
SET status = :status,
    total_items = :totalItems,
    applied_items = :appliedItems,
    rejected_items = :rejectedItems,
    applied_at = :appliedAt,
    rejected_at = :rejectedAt
WHERE id = :changeSetId
