WITH updated AS (
    UPDATE hints
    SET image_resource_id = NULL,
        updated_at = :now
    WHERE id = :id
    RETURNING *
)
