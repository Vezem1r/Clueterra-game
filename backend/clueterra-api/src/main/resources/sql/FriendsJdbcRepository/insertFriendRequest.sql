INSERT INTO user_friendships (id, user_low_id, user_high_id, requested_by, status, created_at, updated_at)
SELECT :id,
       LEAST(:userId, :targetUserId),
       GREATEST(:userId, :targetUserId),
       :userId,
       'PENDING',
       NOW(),
       NOW()
WHERE :userId <> :targetUserId
  AND NOT EXISTS (
    SELECT 1
    FROM user_blocks b
    WHERE (b.blocker_id = :userId AND b.blocked_id = :targetUserId)
       OR (b.blocker_id = :targetUserId AND b.blocked_id = :userId)
  )
ON CONFLICT (user_low_id, user_high_id) DO NOTHING
RETURNING id
