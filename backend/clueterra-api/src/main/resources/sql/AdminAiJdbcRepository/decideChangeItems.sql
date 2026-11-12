SELECT change_set_status,
       total_items,
       applied_items,
       rejected_items,
       affected_items
FROM cl_ai_schema.decide_change_items(
    :changeSetId,
    CAST(:itemTypes AS TEXT[]),
    :decision
)
