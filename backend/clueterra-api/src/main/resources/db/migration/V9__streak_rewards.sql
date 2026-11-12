INSERT INTO app_settings (key, value) VALUES
    ('streak_max_bonus_percent', '50'),
    ('streak_max_days', '365'),
    ('streak_curve_power', '0.4'),
    ('streak_freeze_max', '5'),
    ('streak_freeze_streak_interval', '10'),
    ('streak_freeze_per_badge', '1')
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION calc_streak_bonus(
    p_streak INTEGER,
    p_max_bonus INTEGER,
    p_max_days INTEGER,
    p_curve_power DOUBLE PRECISION
) RETURNS DOUBLE PRECISION AS $$
BEGIN
    IF p_streak <= 0 OR p_max_bonus <= 0 OR p_max_days <= 0 THEN
        RETURN 0;
    END IF;
    RETURN p_max_bonus * POWER(
        LEAST(p_streak, p_max_days)::DOUBLE PRECISION / p_max_days::DOUBLE PRECISION,
        p_curve_power
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE;
