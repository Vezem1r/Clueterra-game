SELECT COUNT(*)
FROM badges b
         LEFT JOIN user_badges ub ON b.id = ub.badge_id AND ub.user_id = :userId
WHERE b.is_active = TRUE
  AND (ub.id IS NOT NULL OR b.is_secret = FALSE)
  AND (:filter = 'all'
    OR (:filter = 'earned' AND ub.id IS NOT NULL)
    OR (:filter = 'locked' AND ub.id IS NULL))
