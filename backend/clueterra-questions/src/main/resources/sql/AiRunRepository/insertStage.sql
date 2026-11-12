INSERT INTO cl_ai_schema.ai_run_stage (
    id, run_id, stage_index, stage_type, status, item_count, estimated_input_tokens,
    estimated_output_tokens, actual_input_tokens, actual_output_tokens, image_count,
    payload, result_summary, error_message, provider_request_id, created_at, started_at, completed_at
)
VALUES (
    :id, :runId, :stageIndex, :stageType, :status, :itemCount, :estimatedInputTokens,
    :estimatedOutputTokens, :actualInputTokens, :actualOutputTokens, :imageCount,
    :payload::jsonb, :resultSummary::jsonb, :errorMessage, :providerRequestId, :createdAt, :startedAt, :completedAt
)
ON CONFLICT (id) DO UPDATE
SET run_id = EXCLUDED.run_id,
    stage_index = EXCLUDED.stage_index,
    stage_type = EXCLUDED.stage_type,
    status = EXCLUDED.status,
    item_count = EXCLUDED.item_count,
    estimated_input_tokens = EXCLUDED.estimated_input_tokens,
    estimated_output_tokens = EXCLUDED.estimated_output_tokens,
    actual_input_tokens = EXCLUDED.actual_input_tokens,
    actual_output_tokens = EXCLUDED.actual_output_tokens,
    image_count = EXCLUDED.image_count,
    payload = EXCLUDED.payload,
    result_summary = EXCLUDED.result_summary,
    error_message = EXCLUDED.error_message,
    provider_request_id = EXCLUDED.provider_request_id,
    created_at = EXCLUDED.created_at,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at
