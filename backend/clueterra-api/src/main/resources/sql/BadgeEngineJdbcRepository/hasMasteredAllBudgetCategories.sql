SELECT EXISTS (
    SELECT 1
    FROM cl_budget_schema.question question
    WHERE question.is_published = TRUE
)
AND NOT EXISTS (
    SELECT DISTINCT question.category
    FROM cl_budget_schema.question question
    WHERE question.is_published = TRUE
    EXCEPT
    SELECT progress.category
    FROM user_budget_project_progress progress
    WHERE progress.user_id = :userId
      AND progress.first_mastered_at IS NOT NULL
)
