DELETE FROM user_friendships
WHERE id = :requestId
  AND status = 'PENDING'
  AND (user_low_id = :userId OR user_high_id = :userId)
