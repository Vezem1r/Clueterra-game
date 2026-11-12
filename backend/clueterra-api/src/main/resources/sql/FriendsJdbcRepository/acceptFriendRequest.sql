UPDATE user_friendships
SET status = 'ACCEPTED',
    responded_at = NOW(),
    updated_at = NOW()
WHERE id = :requestId
  AND status = 'PENDING'
  AND requested_by <> :userId
  AND (user_low_id = :userId OR user_high_id = :userId)
RETURNING id
