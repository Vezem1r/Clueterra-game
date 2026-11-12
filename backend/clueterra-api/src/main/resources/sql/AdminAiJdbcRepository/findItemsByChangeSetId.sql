SELECT *
FROM cl_ai_schema.ai_hint_change_item
WHERE change_set_id = :changeSetId
ORDER BY created_at, id
