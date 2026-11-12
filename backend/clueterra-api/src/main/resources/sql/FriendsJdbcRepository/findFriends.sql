WITH friendships AS (
    SELECT f.id,
           f.created_at,
           CASE WHEN f.user_low_id = :userId THEN f.user_high_id ELSE f.user_low_id END AS peer_id
    FROM user_friendships f
    WHERE f.status = 'ACCEPTED'
      AND (f.user_low_id = :userId OR f.user_high_id = :userId)
)
SELECT fr.id                                      AS friendship_id,
       fr.created_at                              AS friends_since,
       u.id                                       AS user_id,
       u.username                                 AS username,
       COALESCE(p.display_name, u.username)       AS display_name,
       COALESCE(p.avatar_url, '')                 AS avatar_url,
       COALESCE(p.total_xp, 0)                    AS total_xp,
       COALESCE(p.current_level, 1)               AS level
FROM friendships fr
         JOIN users u ON u.id = fr.peer_id
         LEFT JOIN user_profiles p ON p.user_id = u.id
WHERE u.enabled = TRUE
ORDER BY fr.created_at DESC
