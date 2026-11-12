SELECT COUNT(*)
FROM badges b
LEFT JOIN user_badges ub ON b.id = ub.badge_id AND ub.user_id = :userId
WHERE b.is_active = TRUE AND b.is_secret = TRUE AND ub.id IS NULL
