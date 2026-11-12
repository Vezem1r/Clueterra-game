SELECT cgs.country_id,
       c.iso_alpha2                   AS country_code,
       c.name_en                      AS country_name,
       SUM(cgs.times_featured)        AS times_featured,
       SUM(cgs.times_guessed_correct) AS times_guessed_correct,
       COALESCE(
               SUM(cgs.avg_attempts_needed * cgs.times_featured)
                   / NULLIF(SUM(cgs.times_featured), 0), 0
       )                              AS avg_attempts
FROM country_guess_statistics cgs
         JOIN countries c ON c.id = cgs.country_id
WHERE cgs.date BETWEEN :startDate AND :endDate
GROUP BY cgs.country_id, c.iso_alpha2, c.name_en
ORDER BY times_featured DESC
LIMIT :limit
