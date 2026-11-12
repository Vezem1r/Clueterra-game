WITH new_user AS (
    INSERT INTO users (id, role, enabled, reports_muted, is_guest, guest_token, guest_token_expires_at,
                       terms_version_accepted, privacy_version_accepted, legal_accepted_at, created_at, updated_at)
    VALUES (:id, 'USER', true, false, true, :guestToken, :expiresAt,
            :termsVersion, :privacyVersion, NOW(), NOW(), NOW())
    RETURNING id)
INSERT INTO user_profiles (user_id, total_xp, current_level, challenges_played,
                           challenges_won, current_streak, best_streak, created_at, updated_at)
SELECT id, 0, 1, 0, 0, 0, 0, NOW(), NOW() FROM new_user
