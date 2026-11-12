SELECT b.id,
       b.slug,
       b.image_url,
       b.accent_color_key,
       b.effect_color_key,
       COALESCE(t_loc.name, t_en.name)                                 AS name,
       COALESCE(t_loc.description_unlocked, t_en.description_unlocked) AS description,
       TRUE                                                            AS unlocked,
       ub.granted_at::TEXT                                             AS granted_at
FROM badges b
         JOIN user_badges ub ON b.id = ub.badge_id
    AND ub.user_id = (SELECT id FROM users WHERE LOWER(username) = LOWER(:username))
         LEFT JOIN badge_translations t_loc ON b.id = t_loc.badge_id AND t_loc.locale = :locale
         LEFT JOIN badge_translations t_en ON b.id = t_en.badge_id AND t_en.locale = 'en'
WHERE b.is_active = TRUE
ORDER BY ub.granted_at DESC,
         b.display_order,
         b.created_at
LIMIT :pageSize OFFSET :offset
