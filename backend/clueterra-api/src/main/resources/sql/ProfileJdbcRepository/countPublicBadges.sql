SELECT COUNT(*)
FROM badges b
         JOIN user_badges ub ON b.id = ub.badge_id
    AND ub.user_id = (SELECT id FROM users WHERE LOWER(username) = LOWER(:username))
WHERE b.is_active = TRUE
