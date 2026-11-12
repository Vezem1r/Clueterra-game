SELECT cl_media_schema.record_validation(
    :assetId,
    :validationStatus,
    :reason,
    NULL
) AS consecutive_failures
