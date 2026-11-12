SELECT question.iso2,
       question.region,
       question.subregion,
       question.names::TEXT AS names,
       (SELECT (hint.payload -> 'capitals')::TEXT
        FROM cl_schema.flag_game_hint hint
        WHERE hint.question_id = question.id AND hint.kind = 'CAPITAL') AS capitals
FROM cl_schema.flag_game_question question
WHERE question.id = :questionId
