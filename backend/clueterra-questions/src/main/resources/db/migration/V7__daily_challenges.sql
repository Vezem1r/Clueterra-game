CREATE TABLE IF NOT EXISTS daily_challenges (
    id              BIGINT      PRIMARY KEY,
    challenge_date  DATE        NOT NULL,
    challenge_level VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    countries_json  TEXT        NOT NULL,
    hints_json      TEXT        NOT NULL,
    difficulty      VARCHAR(20) NOT NULL,
    total_countries INTEGER     NOT NULL DEFAULT 5,
    total_hints     INTEGER     NOT NULL DEFAULT 25,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_challenges_date_level UNIQUE (challenge_date, challenge_level),
    CONSTRAINT chk_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    CONSTRAINT chk_challenge_level CHECK (challenge_level IN ('EASY', 'MEDIUM', 'HARD'))
);

CREATE INDEX IF NOT EXISTS idx_daily_challenges_date_level ON daily_challenges (challenge_date, challenge_level);

CREATE INDEX IF NOT EXISTS idx_daily_challenges_active ON daily_challenges (is_active) WHERE is_active = TRUE;

COMMENT ON TABLE daily_challenges IS 'Pre-generated daily challenges with countries and hints';

COMMENT ON COLUMN daily_challenges.challenge_date IS 'The date this challenge is for';

COMMENT ON COLUMN daily_challenges.challenge_level IS 'The level category for the day (EASY, MEDIUM, HARD)';

COMMENT ON COLUMN daily_challenges.countries_json IS 'JSON array of country info for this challenge';

COMMENT ON COLUMN daily_challenges.hints_json IS 'JSON array of hints for all countries';

COMMENT ON COLUMN daily_challenges.difficulty IS 'Overall difficulty: EASY, MEDIUM, HARD, EXPERT';

COMMENT ON COLUMN daily_challenges.total_countries IS 'Number of countries in this challenge (usually 5)';

COMMENT ON COLUMN daily_challenges.total_hints IS 'Total number of hints (usually 25 = 5 countries × 5 hints)';
