WITH requests AS (
    SELECT f.id,
           f.created_at,
           CASE WHEN f.user_low_id = :userId THEN f.user_high_id ELSE f.user_low_id END AS peer_id
    FROM user_friendships f
    WHERE f.status = 'PENDING'
      AND f.requested_by = :userId
)
SELECT r.id                                      AS request_id,
       r.created_at                              AS created_at,
       'OUTGOING'                                AS direction,
       u.id                                      AS user_id,
       u.username                                AS username,
       COALESCE(p.display_name, u.username)      AS display_name,
       COALESCE(p.avatar_url, '')                AS avatar_url,
       COALESCE(p.total_xp, 0)                   AS total_xp,
       COALESCE(p.current_level, 1)              AS level
FROM requests r
         JOIN users u ON u.id = r.peer_id
         LEFT JOIN user_profiles p ON p.user_id = u.id
ORDER BY r.created_at DESC
