DELETE FROM cl_media_schema.maintenance_lease
WHERE lease_name = :leaseName
  AND owner_id = :ownerId
