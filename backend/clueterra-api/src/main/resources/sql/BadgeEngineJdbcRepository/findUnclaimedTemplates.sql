SELECT b.id, b.grant_config::TEXT AS grant_config
FROM badges b
JOIN users u ON u.id = :userId
LEFT JOIN user_badges ub ON b.id = ub.badge_id AND ub.user_id = :userId
WHERE b.is_active = TRUE
  AND b.grant_type = 'TEMPLATE'
  AND u.enabled = TRUE
  AND ub.id IS NULL
