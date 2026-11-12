SELECT ub.id, ub.badge_id, b.slug, b.image_url, b.accent_color_key, b.effect_color_key,
       COALESCE(t_loc.name, t_en.name) AS name,
       COALESCE(t_loc.description_unlocked, t_en.description_unlocked) AS description,
       ub.granted_at::TEXT AS granted_at
FROM user_badges ub
JOIN badges b ON b.id = ub.badge_id
LEFT JOIN badge_translations t_loc ON b.id = t_loc.badge_id AND t_loc.locale = :locale
LEFT JOIN badge_translations t_en  ON b.id = t_en.badge_id  AND t_en.locale = 'en'
WHERE ub.user_id = :userId AND ub.is_acknowledged = FALSE AND b.is_active = TRUE
ORDER BY ub.granted_at DESC
