CREATE TABLE IF NOT EXISTS cl_api_schema.refresh_tokens (
    token_hash CHAR(64)                 PRIMARY KEY,
    user_id    BIGINT                   NOT NULL REFERENCES cl_api_schema.users (id) ON DELETE CASCADE,
    family_id  UUID                     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    used_at    TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON cl_api_schema.refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON cl_api_schema.refresh_tokens (family_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON cl_api_schema.refresh_tokens (expires_at);

CREATE OR REPLACE FUNCTION cl_api_schema.rotate_refresh_token(
    p_token_hash CHAR(64),
    p_new_token_hash CHAR(64),
    p_expires_at TIMESTAMP WITH TIME ZONE,
    p_reuse_grace_seconds INTEGER
) RETURNS TABLE(result_status TEXT, result_user_id BIGINT) AS $$
DECLARE
    v_token cl_api_schema.refresh_tokens%ROWTYPE;
BEGIN
    SELECT * INTO v_token
    FROM cl_api_schema.refresh_tokens
    WHERE token_hash = p_token_hash
    FOR UPDATE;

    IF NOT FOUND OR v_token.revoked_at IS NOT NULL OR v_token.expires_at <= NOW() THEN
        RETURN QUERY SELECT 'INVALID'::TEXT, NULL::BIGINT;
        RETURN;
    END IF;

    IF v_token.used_at IS NOT NULL
            AND v_token.used_at < NOW() - make_interval(secs => p_reuse_grace_seconds) THEN
        UPDATE cl_api_schema.refresh_tokens
        SET revoked_at = NOW()
        WHERE family_id = v_token.family_id AND revoked_at IS NULL;
        RETURN QUERY SELECT 'REUSED'::TEXT, v_token.user_id;
        RETURN;
    END IF;

    UPDATE cl_api_schema.refresh_tokens
    SET used_at = COALESCE(used_at, NOW())
    WHERE token_hash = p_token_hash;

    INSERT INTO cl_api_schema.refresh_tokens (token_hash, user_id, family_id, expires_at)
    VALUES (p_new_token_hash, v_token.user_id, v_token.family_id, p_expires_at);

    RETURN QUERY SELECT 'ROTATED'::TEXT, v_token.user_id;
END;
$$ LANGUAGE plpgsql;
