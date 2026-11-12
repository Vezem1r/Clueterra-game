SELECT COUNT(*) FILTER (
           WHERE hint.review_status = 'ACTION_PENDING'
              OR (hint.review_status = 'REVIEWED'
                  AND (hint.review_retry_after_at IS NULL OR hint.review_retry_after_at > NOW()))
       ) AS processed_hints,
       COUNT(*) AS total_hints,
       COUNT(*) FILTER (
           WHERE hint.review_status IN ('NOT_REVIEWED', 'STALE', 'FAILED')
              OR (hint.review_status = 'REVIEWED' AND hint.review_retry_after_at <= NOW())
       ) AS pending_hints,
       COUNT(*) FILTER (WHERE hint.review_status = 'IN_REVIEW') AS in_review_hints,
       COUNT(*) FILTER (WHERE hint.review_status = 'ACTION_PENDING') AS action_pending_hints
FROM cl_schema.hints hint
WHERE hint.is_active = TRUE
  AND hint.source NOT IN ('MAPILLARY', 'AI_GENERATED')
