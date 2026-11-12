CREATE TABLE IF NOT EXISTS badge_categories (
    id            BIGINT       PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE badges
    ADD CONSTRAINT badges_category_id_fkey FOREIGN KEY (category_id) REFERENCES badge_categories (id) ON DELETE SET NULL;
