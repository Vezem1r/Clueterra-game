CREATE TABLE IF NOT EXISTS user_friendships (
    id           BIGINT                   PRIMARY KEY,
    user_low_id  BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_high_id BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_by BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP WITH TIME ZONE,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_friendship_order CHECK (user_low_id < user_high_id),
    CONSTRAINT chk_friendship_requested_by CHECK (requested_by IN (user_low_id, user_high_id)),
    CONSTRAINT chk_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_friendships_pair
    ON user_friendships (user_low_id, user_high_id);

CREATE INDEX IF NOT EXISTS idx_user_friendships_low_status
    ON user_friendships (user_low_id, status);

CREATE INDEX IF NOT EXISTS idx_user_friendships_high_status
    ON user_friendships (user_high_id, status);

CREATE INDEX IF NOT EXISTS idx_user_friendships_requested_by
    ON user_friendships (requested_by, status, created_at DESC);

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked
    ON user_blocks (blocked_id);

CREATE TABLE IF NOT EXISTS user_xp_events (
    id              BIGINT                   PRIMARY KEY,
    user_id         BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id      BIGINT                   REFERENCES game_sessions (id) ON DELETE SET NULL,
    source_type     VARCHAR(40)              NOT NULL,
    source_id       BIGINT,
    challenge_level VARCHAR(10),
    xp_awarded      INTEGER                  NOT NULL,
    week_start      DATE                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_xp_events_positive CHECK (xp_awarded >= 0),
    CONSTRAINT chk_user_xp_events_source CHECK (
        source_type IN ('DAILY_CHALLENGE', 'EVENT', 'PRACTICE', 'ADMIN_ADJUSTMENT', 'RIVER_GAME', 'SILHOUETTE_GAME', 'FLAG_GAME',
                        'QUIZ_GAME')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_xp_events_session
    ON user_xp_events (session_id)
    WHERE session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_xp_events_user_week
    ON user_xp_events (user_id, week_start);

CREATE INDEX IF NOT EXISTS idx_user_xp_events_source
    ON user_xp_events (source_type, source_id, week_start);

CREATE UNIQUE INDEX uq_river_game_xp_event
    ON cl_api_schema.user_xp_events (user_id, source_id)
    WHERE source_type = 'RIVER_GAME';

CREATE UNIQUE INDEX uq_silhouette_game_xp_event
    ON cl_api_schema.user_xp_events (user_id, source_id)
    WHERE source_type = 'SILHOUETTE_GAME';

CREATE UNIQUE INDEX uq_flag_game_xp_event
    ON cl_api_schema.user_xp_events (user_id, source_id)
    WHERE source_type = 'FLAG_GAME';

CREATE UNIQUE INDEX uq_quiz_game_xp_event
    ON cl_api_schema.user_xp_events (user_id, source_id)
    WHERE source_type = 'QUIZ_GAME';

CREATE TABLE IF NOT EXISTS weekly_xp_snapshots (
    user_id    BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week_start DATE                     NOT NULL,
    xp_earned  INTEGER                  NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, week_start),
    CONSTRAINT chk_weekly_xp_non_negative CHECK (xp_earned >= 0)
);

CREATE INDEX IF NOT EXISTS idx_weekly_xp_snapshots_rank
    ON weekly_xp_snapshots (week_start, xp_earned DESC, user_id ASC);

CREATE OR REPLACE FUNCTION record_user_xp_event(
    p_event_id BIGINT,
    p_user_id BIGINT,
    p_session_id BIGINT,
    p_xp_awarded INTEGER,
    p_source_type TEXT,
    p_source_id BIGINT,
    p_challenge_level TEXT,
    p_occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
) RETURNS BOOLEAN AS
$$
DECLARE
    v_week_start DATE := DATE_TRUNC('week', COALESCE(p_occurred_at, NOW()) AT TIME ZONE 'UTC')::DATE;
    v_inserted INTEGER := 0;
BEGIN
    IF p_event_id IS NULL OR p_user_id IS NULL OR p_xp_awarded IS NULL OR p_xp_awarded < 0 THEN
        RETURN FALSE;
    END IF;

    INSERT INTO user_xp_events (id, user_id, session_id, source_type, source_id,
                                challenge_level, xp_awarded, week_start, occurred_at)
    VALUES (p_event_id, p_user_id, p_session_id, p_source_type, p_source_id,
            p_challenge_level, p_xp_awarded, v_week_start, COALESCE(p_occurred_at, NOW()))
    ON CONFLICT DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    IF v_inserted = 0 THEN
        RETURN FALSE;
    END IF;

    INSERT INTO weekly_xp_snapshots (user_id, week_start, xp_earned, updated_at)
    VALUES (p_user_id, v_week_start, p_xp_awarded, NOW())
    ON CONFLICT (user_id, week_start) DO UPDATE SET
        xp_earned = weekly_xp_snapshots.xp_earned + EXCLUDED.xp_earned,
        updated_at = NOW();

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reassign_session_xp_event(
    p_session_id BIGINT,
    p_target_user_id BIGINT,
    p_xp_awarded INTEGER
) RETURNS BOOLEAN AS
$$
DECLARE
    v_old_user_id BIGINT;
    v_week_start DATE;
    v_old_xp_awarded INTEGER;
BEGIN
    IF p_session_id IS NULL OR p_target_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    SELECT user_id, week_start, xp_awarded
    INTO v_old_user_id, v_week_start, v_old_xp_awarded
    FROM user_xp_events
    WHERE session_id = p_session_id
    FOR UPDATE;

    IF v_old_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    UPDATE weekly_xp_snapshots
    SET xp_earned = GREATEST(0, xp_earned - COALESCE(v_old_xp_awarded, 0)),
        updated_at = NOW()
    WHERE user_id = v_old_user_id
      AND week_start = v_week_start;

    UPDATE user_xp_events
    SET user_id = p_target_user_id,
        xp_awarded = COALESCE(p_xp_awarded, xp_awarded)
    WHERE session_id = p_session_id;

    INSERT INTO weekly_xp_snapshots (user_id, week_start, xp_earned, updated_at)
    VALUES (p_target_user_id, v_week_start, COALESCE(p_xp_awarded, v_old_xp_awarded, 0), NOW())
    ON CONFLICT (user_id, week_start) DO UPDATE SET
        xp_earned = weekly_xp_snapshots.xp_earned + EXCLUDED.xp_earned,
        updated_at = NOW();

    DELETE FROM weekly_xp_snapshots
    WHERE user_id = v_old_user_id
      AND week_start = v_week_start
      AND xp_earned = 0;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;
