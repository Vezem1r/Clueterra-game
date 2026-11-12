WITH recent_batches AS (SELECT change_batch_id,
                               MAX(changed_at) AS batch_changed_at
                        FROM app_settings_audit
                        GROUP BY change_batch_id
                        ORDER BY batch_changed_at DESC
                        LIMIT :limit OFFSET :offset)
SELECT a.id,
       a.change_batch_id,
       a.setting_key,
       a.old_value,
       a.new_value,
       a.changed_at,
       a.changed_by_user_id,
       a.changed_by_email,
       a.changed_by_username
FROM app_settings_audit a
         JOIN recent_batches rb ON rb.change_batch_id = a.change_batch_id
ORDER BY rb.batch_changed_at DESC, a.id
