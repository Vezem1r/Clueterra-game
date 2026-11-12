SELECT u.id                                      AS user_id,
       u.username                                AS username,
       COALESCE(p.display_name, u.username)      AS display_name,
       COALESCE(p.avatar_url, '')                AS avatar_url,
       COALESCE(p.total_xp, 0)                   AS total_xp,
       COALESCE(p.current_level, 1)              AS level
FROM users u
         LEFT JOIN user_profiles p ON p.user_id = u.id
WHERE u.id <> :userId
  AND u.enabled = TRUE
  AND u.is_guest = FALSE
  AND u.social_discoverable = TRUE
  AND (
    LOWER(u.username) LIKE :query
        OR LOWER(COALESCE(p.display_name, '')) LIKE :query
    )
  AND NOT EXISTS (
    SELECT 1
    FROM user_blocks b
    WHERE (b.blocker_id = :userId AND b.blocked_id = u.id)
       OR (b.blocker_id = u.id AND b.blocked_id = :userId)
  )
ORDER BY p.total_xp DESC NULLS LAST, u.username
LIMIT :limit
