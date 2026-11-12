INSERT INTO cl_ai_schema.ai_run_usage (
    id, run_id, stage_id, request_index, provider_request_id, finish_reason, estimated_input_tokens,
    prompt_tokens, completion_tokens, total_tokens, tool_call_count, image_input_count, latency_ms, metadata, created_at
)
VALUES (
    :id, :runId, :stageId, :requestIndex, :providerRequestId, :finishReason, :estimatedInputTokens,
    :promptTokens, :completionTokens, :totalTokens, :toolCallCount, :imageInputCount, :latencyMs, :metadata::jsonb, :createdAt
)
ON CONFLICT (id) DO UPDATE
SET run_id = EXCLUDED.run_id,
    stage_id = EXCLUDED.stage_id,
    request_index = EXCLUDED.request_index,
    provider_request_id = EXCLUDED.provider_request_id,
    finish_reason = EXCLUDED.finish_reason,
    estimated_input_tokens = EXCLUDED.estimated_input_tokens,
    prompt_tokens = EXCLUDED.prompt_tokens,
    completion_tokens = EXCLUDED.completion_tokens,
    total_tokens = EXCLUDED.total_tokens,
    tool_call_count = EXCLUDED.tool_call_count,
    image_input_count = EXCLUDED.image_input_count,
    latency_ms = EXCLUDED.latency_ms,
    metadata = EXCLUDED.metadata,
    created_at = EXCLUDED.created_at
