CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS cl_api_schema;

CREATE SCHEMA IF NOT EXISTS cl_api_sch_schema;

CREATE TABLE IF NOT EXISTS cl_api_schema.users (
    id                       BIGINT                   PRIMARY KEY,
    email                    VARCHAR(255),
    password_hash            VARCHAR(255),
    username                 VARCHAR(50),
    role                     VARCHAR(50)              NOT NULL DEFAULT 'USER',
    enabled                  BOOLEAN                  NOT NULL DEFAULT TRUE,
    reports_muted            BOOLEAN                  NOT NULL DEFAULT FALSE,
    oauth_provider           VARCHAR(20),
    oauth_id                 VARCHAR(255),
    avatar_url               VARCHAR(512),
    totp_secret              VARCHAR(255),
    totp_enabled             BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    profile_locked           BOOLEAN                  DEFAULT FALSE NOT NULL,
    terms_version_accepted   VARCHAR(32),
    privacy_version_accepted VARCHAR(32),
    legal_accepted_at        TIMESTAMP WITH TIME ZONE,
    is_guest                 BOOLEAN                  NOT NULL DEFAULT FALSE,
    guest_token              VARCHAR(64),
    guest_token_expires_at   TIMESTAMP WITH TIME ZONE,
    social_discoverable      BOOLEAN                  NOT NULL DEFAULT TRUE,
    totp_last_used_step      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_role ON cl_api_schema.users (role);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_oauth ON cl_api_schema.users (oauth_provider, oauth_id)
    WHERE oauth_provider IS NOT NULL AND oauth_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_guest_token ON users (guest_token) WHERE guest_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_is_guest ON users (is_guest) WHERE is_guest = TRUE;

CREATE UNIQUE INDEX uq_users_email_lower
    ON cl_api_schema.users (LOWER(email)) WHERE email IS NOT NULL;

CREATE UNIQUE INDEX uq_users_username_lower
    ON cl_api_schema.users (LOWER(username)) WHERE username IS NOT NULL;
