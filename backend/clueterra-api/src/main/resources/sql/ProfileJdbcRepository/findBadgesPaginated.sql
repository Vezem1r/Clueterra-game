SELECT b.id,
       b.slug,
       b.image_url,
       b.accent_color_key,
       b.effect_color_key,
       COALESCE(t_loc.name, t_en.name) AS name,
       CASE
           WHEN ub.id IS NOT NULL
               THEN COALESCE(t_loc.description_unlocked, t_en.description_unlocked)
           ELSE COALESCE(t_loc.description_locked, t_en.description_locked)
           END                         AS description,
       ub.id IS NOT NULL               AS unlocked,
       ub.granted_at::TEXT             AS granted_at
FROM badges b
         LEFT JOIN badge_translations t_loc ON b.id = t_loc.badge_id AND t_loc.locale = :locale
         LEFT JOIN badge_translations t_en ON b.id = t_en.badge_id AND t_en.locale = 'en'
         LEFT JOIN user_badges ub ON b.id = ub.badge_id AND ub.user_id = :userId
WHERE b.is_active = TRUE
  AND (ub.id IS NOT NULL OR b.is_secret = FALSE)
  AND (:filter = 'all'
    OR (:filter = 'earned' AND ub.id IS NOT NULL)
    OR (:filter = 'locked' AND ub.id IS NULL))
ORDER BY CASE WHEN ub.id IS NOT NULL THEN 0 ELSE 1 END,
         ub.granted_at DESC NULLS LAST,
         b.display_order,
         b.created_at
LIMIT :pageSize OFFSET :offset
