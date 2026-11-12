INSERT INTO hints (id, country_id, category, hint_type, hint_text, difficulty,
                   quality_score, source, source_indicator, source_year,
                   content_hash, ambiguity_count, times_used, times_correct,
                   is_active, created_at, updated_at, editorial_text_locked)
VALUES (:id, :countryId, :category, :hintType, :hintText, :difficulty,
        :qualityScore, 'MANUAL', NULL, :sourceYear,
        :contentHash, NULL, 0, 0,
        true, :now, :now, true)
