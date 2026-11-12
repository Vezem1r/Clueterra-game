SELECT b.id,
       b.slug,
       b.image_url,
       b.accent_color_key,
       b.effect_color_key,
       COALESCE(t_loc.name, t_en.name)                                 AS name,
       COALESCE(t_loc.description_unlocked, t_en.description_unlocked) AS description,
       FALSE                                                           AS unlocked,
       NULL                                                            AS granted_at
FROM badges b
         LEFT JOIN badge_translations t_loc ON b.id = t_loc.badge_id AND t_loc.locale = :locale
         LEFT JOIN badge_translations t_en ON b.id = t_en.badge_id AND t_en.locale = 'en'
WHERE b.is_active = TRUE
  AND b.is_secret = FALSE
ORDER BY RANDOM()
LIMIT :limit
