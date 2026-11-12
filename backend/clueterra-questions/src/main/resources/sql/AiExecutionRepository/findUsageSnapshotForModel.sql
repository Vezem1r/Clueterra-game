SELECT COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:minuteWindowStart AS timestamptz)
                       THEN (1 + COALESCE((usage.metadata ->> 'intermediateResponses')::int, 0))
                   ELSE 0
                   END
       ), 0)                                                                 AS requests_last_minute,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:minuteWindowStart AS timestamptz)
                       THEN COALESCE(NULLIF(usage.prompt_tokens, 0), usage.estimated_input_tokens)
                   ELSE 0
                   END
       ), 0)                                                                 AS input_tokens_last_minute,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:dayWindowStart AS timestamptz)
                       AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
                       THEN (1 + COALESCE((usage.metadata ->> 'intermediateResponses')::int, 0))
                   ELSE 0
                   END
       ), 0)                                                                 AS requests_today,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:dayWindowStart AS timestamptz)
                       AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
                       AND COALESCE((usage.metadata ->> 'googleSearchEnabled')::boolean, FALSE)
                       THEN 1
                   ELSE 0
                   END
       ), 0)                                                                 AS grounded_prompts_today,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:dayWindowStart AS timestamptz)
                       AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
                       AND usage.finish_reason LIKE 'ERROR_%'
                       THEN 1
                   ELSE 0
                   END
       ), 0)                                                                 AS failed_requests_today,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:dayWindowStart AS timestamptz)
                       AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
                       THEN COALESCE(NULLIF(usage.prompt_tokens, 0), usage.estimated_input_tokens)
                   ELSE 0
                   END
       ), 0)                                                                 AS input_tokens_today,
       COALESCE(SUM(
               CASE
                   WHEN usage.created_at >= CAST(:dayWindowStart AS timestamptz)
                       AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
                       THEN usage.completion_tokens
                   ELSE 0
                   END
       ), 0)                                                                 AS output_tokens_today
FROM cl_ai_schema.ai_run_usage usage
         JOIN cl_ai_schema.ai_run run
              ON run.id = usage.run_id
WHERE run.provider = :provider
  AND run.model = :model
  AND usage.created_at >= CAST(:earliestWindowStart AS timestamptz)
  AND usage.created_at < CAST(:dayWindowEnd AS timestamptz)
