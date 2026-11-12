SELECT p.*, c.iso_alpha2 AS country_code, c.name_en AS country_name
FROM cl_media_schema.ai_hint_proposal_projection p
LEFT JOIN cl_schema.countries c ON c.id = p.country_id
WHERE p.change_set_id = :changeSetId
ORDER BY p.created_at, p.id
