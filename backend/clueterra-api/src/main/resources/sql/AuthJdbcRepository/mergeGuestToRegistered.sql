UPDATE users
SET email = :email,
    username = :username,
    password_hash = :passwordHash,
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
