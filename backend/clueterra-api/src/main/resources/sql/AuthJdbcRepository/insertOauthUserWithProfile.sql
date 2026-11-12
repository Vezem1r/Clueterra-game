WITH new_user AS (
    INSERT INTO users (
        id, email, username, role, enabled, reports_muted,
        oauth_provider, oauth_id, avatar_url,
        terms_version_accepted, privacy_version_accepted, legal_accepted_at,
        created_at, updated_at
    )
        VALUES (
            :id, :email, :username, 'USER', true, false,
            :oauthProvider, :oauthId, :avatarUrl,
            :termsVersion, :privacyVersion, NOW(),
            NOW(), NOW()
        )
        RETURNING id)
INSERT
INTO user_profiles (user_id, display_name, avatar_url, total_xp, current_level,
                    challenges_played, challenges_won, current_streak, best_streak,
                    created_at, updated_at)
SELECT id,
       :username,
       :avatarUrl,
       0,
       1,
       0,
       0,
       0,
       0,
       NOW(),
       NOW()
FROM new_user
