UPDATE countries
SET name_en = COALESCE(NULLIF(name_en, ''), :nameEn),
    name_native = COALESCE(NULLIF(name_native, ''), :nameNative),
    capital = COALESCE(NULLIF(capital, ''), :capital),
    flag_emoji = COALESCE(NULLIF(flag_emoji, ''), :flagEmoji),
    flag_url = COALESCE(NULLIF(flag_url, ''), :flagUrl),
    updated_at = :updatedAt
WHERE id = :id
  AND ((NULLIF(name_en, '') IS NULL AND :nameEn::TEXT IS NOT NULL)
    OR (NULLIF(name_native, '') IS NULL AND :nameNative::TEXT IS NOT NULL)
    OR (NULLIF(capital, '') IS NULL AND :capital::TEXT IS NOT NULL)
    OR (NULLIF(flag_emoji, '') IS NULL AND :flagEmoji::TEXT IS NOT NULL)
    OR (NULLIF(flag_url, '') IS NULL AND :flagUrl::TEXT IS NOT NULL))
RETURNING *
