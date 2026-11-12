INSERT INTO cl_media_schema.maintenance_lease (
    lease_name,
    owner_id,
    lease_until,
    updated_at
)
VALUES (
    :leaseName,
    :ownerId,
    NOW() + MAKE_INTERVAL(secs => :leaseSeconds),
    NOW()
)
ON CONFLICT (lease_name) DO UPDATE
SET owner_id = EXCLUDED.owner_id,
    lease_until = EXCLUDED.lease_until,
    updated_at = NOW()
WHERE cl_media_schema.maintenance_lease.lease_until < NOW()
RETURNING lease_name
