WITH merged_user AS (
    UPDATE users
    SET email = :email,
        username = :username,
        oauth_provider = :oauthProvider,
        oauth_id = :oauthId,
        avatar_url = :avatarUrl,
        is_guest = false,
        guest_token = NULL,
        guest_token_expires_at = NULL,
        terms_version_accepted = :termsVersion,
        privacy_version_accepted = :privacyVersion,
        legal_accepted_at = NOW(),
        updated_at = NOW()
    WHERE guest_token = :guestToken
      AND is_guest = true
      AND guest_token_expires_at > NOW()
    RETURNING id
),
updated_profile AS (
    UPDATE user_profiles
    SET display_name = :username,
        avatar_url = :avatarUrl,
        updated_at = NOW()
    WHERE user_id IN (SELECT id FROM merged_user)
    RETURNING user_id
)
SELECT merged_user.id
FROM merged_user
LEFT JOIN updated_profile ON updated_profile.user_id = merged_user.id
