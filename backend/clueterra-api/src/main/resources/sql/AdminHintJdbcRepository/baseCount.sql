SELECT COUNT(*)
FROM hints h
         JOIN countries c ON c.id = h.country_id
