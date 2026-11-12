INSERT INTO hint_generation_log (id, country_id, batch_id, country_code, trigger_type, hints_attempted, hints_created,
                                 hints_duplicates, hints_failed, avg_difficulty, avg_quality_score,
                                 categories_covered, hint_types_covered, started_at, completed_at,
                                 duration_ms, error_message, details, lens_breakdown)
VALUES (:id, :countryId, :batchId, :countryCode, :triggerType, :hintsAttempted, :hintsCreated,
        :hintsDuplicates, :hintsFailed, :avgDifficulty, :avgQualityScore,
        :categoriesCovered, :hintTypesCovered, :startedAt, :completedAt,
        :durationMs, :errorMessage, :details::jsonb, :lensBreakdown::jsonb)
