SELECT up.user_id,
       u.username,
       COALESCE(up.display_name, u.username, 'Anonymous') AS display_name,
       COALESCE(up.avatar_url, '')                        AS avatar_url,
       up.total_xp,
       up.current_level,
       up.challenges_won,
       ROW_NUMBER() OVER (ORDER BY up.total_xp DESC, up.user_id) AS rank,
       COUNT(*) OVER ()                                        AS total_count
FROM user_profiles up
JOIN users u ON u.id = up.user_id
WHERE u.is_guest = FALSE
ORDER BY up.total_xp DESC, up.user_id
LIMIT :limit OFFSET :offset
