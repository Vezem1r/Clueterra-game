SELECT id,
       grant_type,
       grant_config::TEXT AS grant_config
FROM badges
WHERE is_active = TRUE
  AND id IN (:badgeIds)
