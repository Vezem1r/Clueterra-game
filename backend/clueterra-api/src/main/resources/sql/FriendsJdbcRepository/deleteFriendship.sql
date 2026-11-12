DELETE FROM user_friendships
WHERE user_low_id = LEAST(:userId, :friendId)
  AND user_high_id = GREATEST(:userId, :friendId)
  AND (user_low_id = :userId OR user_high_id = :userId)
