DELETE FROM cl_schema.indicator_config
WHERE indicator_code LIKE 'WMCUR\_%' ESCAPE '\'
   OR indicator_code LIKE 'WMLAND\_%' ESCAPE '\';

TRUNCATE TABLE cl_schema.hint_embedding;
