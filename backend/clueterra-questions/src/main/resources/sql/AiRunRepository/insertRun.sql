INSERT INTO cl_ai_schema.ai_run (
    id, run_type, trigger_type, status, provider, model, prompt_version, requested_by,
    total_stages, prepared_items, processed_items, changed_items, generated_items,
    estimated_input_tokens, estimated_output_tokens, actual_input_tokens, actual_output_tokens,
    tool_call_count, configuration, summary, error_message, created_at, started_at, completed_at
)
VALUES (
    :id, :runType, :triggerType, :status, :provider, :model, :promptVersion, :requestedBy,
    :totalStages, :preparedItems, :processedItems, :changedItems, :generatedItems,
    :estimatedInputTokens, :estimatedOutputTokens, :actualInputTokens, :actualOutputTokens,
    :toolCallCount, :configuration::jsonb, :summary::jsonb, :errorMessage, :createdAt, :startedAt, :completedAt
)
ON CONFLICT (id) DO UPDATE
SET run_type = EXCLUDED.run_type,
    trigger_type = EXCLUDED.trigger_type,
    status = EXCLUDED.status,
    provider = EXCLUDED.provider,
    model = EXCLUDED.model,
    prompt_version = EXCLUDED.prompt_version,
    requested_by = EXCLUDED.requested_by,
    total_stages = EXCLUDED.total_stages,
    prepared_items = EXCLUDED.prepared_items,
    processed_items = EXCLUDED.processed_items,
    changed_items = EXCLUDED.changed_items,
    generated_items = EXCLUDED.generated_items,
    estimated_input_tokens = EXCLUDED.estimated_input_tokens,
    estimated_output_tokens = EXCLUDED.estimated_output_tokens,
    actual_input_tokens = EXCLUDED.actual_input_tokens,
    actual_output_tokens = EXCLUDED.actual_output_tokens,
    tool_call_count = EXCLUDED.tool_call_count,
    configuration = EXCLUDED.configuration,
    summary = EXCLUDED.summary,
    error_message = EXCLUDED.error_message,
    created_at = EXCLUDED.created_at,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at
