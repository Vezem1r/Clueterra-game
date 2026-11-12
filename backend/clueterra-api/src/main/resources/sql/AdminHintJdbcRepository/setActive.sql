WITH updated AS (
    UPDATE hints
    SET is_active = :active,
        updated_at = :now
    WHERE id = :id
    RETURNING *
)
