INSERT INTO cl_ai_schema.ai_hint_change_set (
    id, run_id, change_set_type, status, title, summary, total_items, applied_items, rejected_items,
    metadata, created_at, applied_at, rejected_at
)
VALUES (
    :id, :runId, :changeSetType, :status, :title, :summary, :totalItems, :appliedItems, :rejectedItems,
    :metadata::jsonb, :createdAt, :appliedAt, :rejectedAt
)
ON CONFLICT (id) DO UPDATE
SET run_id = EXCLUDED.run_id,
    change_set_type = EXCLUDED.change_set_type,
    status = EXCLUDED.status,
    title = EXCLUDED.title,
    summary = EXCLUDED.summary,
    total_items = EXCLUDED.total_items,
    applied_items = EXCLUDED.applied_items,
    rejected_items = EXCLUDED.rejected_items,
    metadata = EXCLUDED.metadata,
    created_at = EXCLUDED.created_at,
    applied_at = EXCLUDED.applied_at,
    rejected_at = EXCLUDED.rejected_at
