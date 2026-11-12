WITH friend_ids AS (
    SELECT :userId::BIGINT AS user_id
    UNION
    SELECT CASE WHEN f.user_low_id = :userId THEN f.user_high_id ELSE f.user_low_id END
    FROM user_friendships f
    WHERE f.status = 'ACCEPTED'
      AND (f.user_low_id = :userId OR f.user_high_id = :userId)
),
ranked AS (
    SELECT u.id                                      AS user_id,
           u.username                                AS username,
           COALESCE(p.display_name, u.username)      AS display_name,
           COALESCE(p.avatar_url, '')                AS avatar_url,
           COALESCE(p.total_xp, 0)                   AS total_xp,
           COALESCE(p.current_level, 1)              AS level,
           COALESCE(p.total_xp, 0)                   AS score_xp,
           ROW_NUMBER() OVER (
               ORDER BY COALESCE(p.total_xp, 0) DESC, u.id
           )                                         AS rank
    FROM friend_ids f
             JOIN users u ON u.id = f.user_id
             LEFT JOIN user_profiles p ON p.user_id = u.id
)
SELECT *,
       user_id = :userId AS "current_user"
FROM ranked
ORDER BY rank
