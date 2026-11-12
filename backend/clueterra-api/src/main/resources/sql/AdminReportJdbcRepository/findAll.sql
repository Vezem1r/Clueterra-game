SELECT hr.id,
       hr.hint_id,
       hr.user_id,
       u.email        AS user_email,
       COALESCE(h.hint_text, hh.hint_text, '[missing hint]') AS hint_text,
       c.name_en      AS country_name,
       hr.report_type,
       hr.description,
       hr.status,
       hr.admin_notes,
       hr.resolved_by AS resolved_by_id,
       rb.email       AS resolved_by_email,
       hr.created_at,
       hr.resolved_at
FROM hint_reports hr
         JOIN users u ON u.id = hr.user_id
         LEFT JOIN hints h ON h.id = hr.hint_id
         LEFT JOIN hints_history hh ON hh.id = hr.hint_id AND h.id IS NULL
         LEFT JOIN countries c ON c.id = COALESCE(h.country_id, hh.country_id)
         LEFT JOIN users rb ON rb.id = hr.resolved_by
