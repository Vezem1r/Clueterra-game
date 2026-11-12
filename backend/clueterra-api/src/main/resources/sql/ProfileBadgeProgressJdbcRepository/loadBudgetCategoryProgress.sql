WITH available_categories AS (
    SELECT DISTINCT question.category
    FROM cl_budget_schema.question question
    WHERE question.is_published = TRUE
), mastered_categories AS (
    SELECT DISTINCT progress.category
    FROM user_budget_project_progress progress
    WHERE progress.user_id = :userId
      AND progress.first_mastered_at IS NOT NULL
)
SELECT
    COUNT(*)::INTEGER AS total_count,
    COUNT(mastered.category)::INTEGER AS mastered_count
FROM available_categories available
LEFT JOIN mastered_categories mastered ON mastered.category = available.category
