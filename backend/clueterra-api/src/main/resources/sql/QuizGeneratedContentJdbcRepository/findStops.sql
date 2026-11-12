SELECT c.id, c.iso2, c.names ->> 'en' AS name_en, c.tier, c.region, c.subregion, c.is_active, c.generated_at,
       array_to_string(c.neighbours, ',') AS neighbours,
       questions.questions,
       questions.accuracy,
       visits.visitors,
       visits.explored
FROM cl_schema.quiz_game_country c
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'id', q.id, 'kind', q.kind, 'level', q.level, 'active', q.is_active,
               'subject', q.subject, 'options', q.options, 'answerIndex', q.answer_index, 'fact', q.fact,
               'answers', given.answers, 'correct', given.correct, 'generatedAt', q.generated_at
           ) ORDER BY q.level, q.kind), '[]'::JSONB)::TEXT AS questions,
           SUM(given.correct)::DOUBLE PRECISION / NULLIF(SUM(given.answers), 0) AS accuracy
    FROM cl_schema.quiz_game_question q
    CROSS JOIN LATERAL (
        SELECT COUNT(*) AS answers, COUNT(*) FILTER (WHERE answer.correct) AS correct
        FROM cl_api_schema.quiz_game_answer answer
        WHERE answer.question_id = q.id
    ) given
    WHERE q.quiz_country_id = c.id AND (:kind = '' OR q.kind = :kind)
) questions
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS visitors, COUNT(*) FILTER (WHERE progress.explored_at IS NOT NULL) AS explored
    FROM cl_api_schema.quiz_game_progress progress
    WHERE progress.quiz_country_id = c.id
) visits
