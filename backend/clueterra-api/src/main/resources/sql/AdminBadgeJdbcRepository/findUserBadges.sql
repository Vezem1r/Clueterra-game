SELECT ub.badge_id, b.slug, b.image_url,
       COALESCE(t.name, te.name) AS name,
       ub.granted_at::TEXT AS granted_at,
       ub.granted_by
FROM user_badges ub
JOIN badges b ON b.id = ub.badge_id
LEFT JOIN badge_translations t ON t.badge_id = b.id AND t.locale = 'en'
LEFT JOIN badge_translations te ON te.badge_id = b.id AND te.locale = 'en'
WHERE ub.user_id = :userId
ORDER BY ub.granted_at DESC
