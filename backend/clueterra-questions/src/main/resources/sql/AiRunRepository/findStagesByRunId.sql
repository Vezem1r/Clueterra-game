SELECT *
FROM cl_ai_schema.ai_run_stage
WHERE run_id = :runId
ORDER BY stage_index ASC
