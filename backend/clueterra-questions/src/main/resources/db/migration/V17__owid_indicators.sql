WITH owid_rows(indicator_code, display_name, category, unit, hint_type, quality_score, base_difficulty, weight,
               lower_is_better, hint_template) AS (VALUES ('OWID_HAPPINESS_TOP'::TEXT, 'World happiness top rank',
                                                           'DEMOGRAPHICS', 'score', 'RANKING', 0.88, 68, 0.85, false,
                                                           'Happiness-wise, this country ranks {rank} in the world with a life satisfaction score of {score} out of 10.'::TEXT),

                                                          ('OWID_HAPPINESS_MID', 'World happiness mid rank',
                                                           'DEMOGRAPHICS', 'score', 'ABSOLUTE_FACT', 0.82, 65, 0.80,
                                                           false,
                                                           'The World Happiness Report places this country at rank {rank} out of {total} nations, with residents rating their lives {score} out of 10.'),

                                                          ('OWID_HAPPINESS_LOW', 'World happiness low rank',
                                                           'DEMOGRAPHICS', 'score', 'RANKING', 0.82, 70, 0.78, false,
                                                           'People here give their lives a {score} out of 10 on the global satisfaction scale — this country ranks {rank} out of {total} in the world happiness index.'),

                                                          ('OWID_HAPPINESS_SCORE', 'World happiness score fact',
                                                           'DEMOGRAPHICS', 'score', 'ABSOLUTE_FACT', 0.80, 62, 0.80,
                                                           false,
                                                           'Gallup''s life satisfaction survey gives this country a score of {score} out of 10.'),

                                                          ('OWID_HAPPINESS_VS_AVG', 'Happiness vs global average',
                                                           'DEMOGRAPHICS', 'score', 'RELATIVE_FACT', 0.80, 64, 0.78,
                                                           false,
                                                           'The average life satisfaction score here is {score}/10 — {diff} points {direction} the global average of {avg}.'),

                                                          ('OWID_HDI_TOP', 'HDI top rank', 'DEMOGRAPHICS', 'index',
                                                           'RANKING', 0.84, 65, 0.82, false,
                                                           'The United Nations ranks this country {rank} out of {total} on the Human Development Index with a score of {value} — firmly in the top tier.'),

                                                          ('OWID_HDI_TIER', 'HDI tier fact', 'DEMOGRAPHICS', 'index',
                                                           'ABSOLUTE_FACT', 0.80, 63, 0.78, false,
                                                           'This country has a Human Development Index of {value}, placing it in the {tier} development bracket (rank {rank}).'),

                                                          ('OWID_HDI_RANK', 'HDI rank', 'DEMOGRAPHICS', 'index',
                                                           'RANKING', 0.82, 67, 0.80, false,
                                                           'Ranked {rank} of {total} countries on the UN Human Development Index — the composite score here is {value}.'),

                                                          ('OWID_HDI_LOW', 'HDI low rank', 'DEMOGRAPHICS', 'index',
                                                           'RANKING', 0.82, 72, 0.80, false,
                                                           'With a Human Development Index of just {value}, this country ranks {rank} out of {total} — one of the lowest-scoring nations on the UN development scale.'),

                                                          ('OWID_DEMOCRACY_HIGH', 'High democracy score', 'POLITICS',
                                                           'index', 'ABSOLUTE_FACT', 0.84, 72, 0.82, false,
                                                           'This country scores {score} out of 1.0 on the liberal democracy index — ranking {rank} globally, firmly among the world''s most democratic nations.'),

                                                          ('OWID_DEMOCRACY_LOW', 'Low democracy score', 'POLITICS',
                                                           'index', 'ABSOLUTE_FACT', 0.84, 74, 0.82, false,
                                                           'With a liberal democracy score of just {score} out of 1.0, this country ranks among the world''s {tier} states.'),

                                                          ('OWID_DEMOCRACY_MID', 'Mid democracy score', 'POLITICS',
                                                           'index', 'ABSOLUTE_FACT', 0.80, 68, 0.78, false,
                                                           'The liberal democracy index gives this country a score of {score}/1.0 — placing it in a {tier} position globally.'),

                                                          ('OWID_DEMOCRACY_RANK', 'Democracy global rank', 'POLITICS',
                                                           'index', 'RANKING', 0.82, 70, 0.80, false,
                                                           'On the V-Dem liberal democracy index, this country ranks {rank} out of {total} nations with a score of {score}/1.0.'),

                                                          ('OWID_SOLAR_HIGH', 'High solar electricity share',
                                                           'ENVIRONMENT', '%', 'ABSOLUTE_FACT', 0.84, 74, 0.82, false,
                                                           'Solar panels supply {pct}% of this country''s electricity — ranking {rank} in the world for solar energy share.'),

                                                          ('OWID_SOLAR_MID', 'Mid solar electricity share',
                                                           'ENVIRONMENT', '%', 'ABSOLUTE_FACT', 0.80, 68, 0.78, false,
                                                           'Around {pct}% of electricity here comes from solar panels — a growing part of the national energy mix.'),

                                                          ('OWID_SOLAR_LOW', 'Low solar electricity share',
                                                           'ENVIRONMENT', '%', 'ABSOLUTE_FACT', 0.78, 65, 0.76, false,
                                                           'Solar panels supply just {pct}% of this country''s electricity — solar power plays a negligible role in the national energy mix.'),

                                                          ('OWID_WIND_HIGH', 'High wind electricity share',
                                                           'ENVIRONMENT', '%', 'ABSOLUTE_FACT', 0.86, 76, 0.84, false,
                                                           'Wind turbines provide {pct}% of this country''s electricity — ranking {rank} globally for wind power share.'),

                                                          ('OWID_WIND_MID', 'Mid wind electricity share', 'ENVIRONMENT',
                                                           '%', 'ABSOLUTE_FACT', 0.80, 70, 0.78, false,
                                                           'About {pct}% of this country''s electricity is generated by wind power.'),

                                                          ('OWID_WIND_LOW', 'Low wind electricity share', 'ENVIRONMENT',
                                                           '%', 'ABSOLUTE_FACT', 0.78, 66, 0.76, false,
                                                           'Wind turbines generate just {pct}% of electricity here — wind power contributes almost nothing to the national grid.'),

                                                          ('OWID_RENEWABLES_HIGH', 'High renewables share',
                                                           'ENVIRONMENT', '%', 'ABSOLUTE_FACT', 0.86, 74, 0.84, false,
                                                           'Renewables account for {pct}% of electricity generation here — ranking {rank} globally and among the world''s cleanest power grids.'),

                                                          ('OWID_RENEWABLES_LOW', 'Low renewables share', 'ENVIRONMENT',
                                                           '%', 'ABSOLUTE_FACT', 0.82, 72, 0.80, false,
                                                           'Only {pct}% of electricity here comes from renewable sources — the grid relies almost entirely on fossil fuels or nuclear.'),

                                                          ('OWID_RENEWABLES_MID', 'Mid renewables share', 'ENVIRONMENT',
                                                           '%', 'ABSOLUTE_FACT', 0.78, 66, 0.76, false,
                                                           'Renewable energy provides {pct}% of electricity here — {direction} the global average for clean power share.'),

                                                          ('OWID_POVERTY_HIGH', 'High extreme poverty share',
                                                           'DEMOGRAPHICS', '%', 'RANKING', 0.86, 78, 0.84, true,
                                                           'Around {pct}% of the population lives on less than $3 a day here — ranking {rank} out of {total} countries for extreme poverty.'),

                                                          ('OWID_POVERTY_LOW', 'Low extreme poverty share',
                                                           'DEMOGRAPHICS', '%', 'ABSOLUTE_FACT', 0.84, 72, 0.82, true,
                                                           'Extreme poverty here is just {pct}% — virtually all residents live above the $3-a-day threshold.'),

                                                          ('OWID_POVERTY_FACT', 'Extreme poverty fact', 'DEMOGRAPHICS',
                                                           '%', 'ABSOLUTE_FACT', 0.80, 70, 0.78, true,
                                                           'About {pct}% of the population survives on less than $3 per day — a {tier} level of extreme poverty.'),

                                                          ('OWID_PERCENTILE_TOP', 'Top percentile', 'DEMOGRAPHICS',
                                                           'rank', 'RANKING', 0.85, 72, 0.83, false,
                                                           'This country falls in the top {pct}% of all nations globally for {indicator}.'),

                                                          ('OWID_PERCENTILE_BOTTOM', 'Bottom percentile',
                                                           'DEMOGRAPHICS', 'rank', 'RANKING', 0.82, 70, 0.80, false,
                                                           'This country ranks in the bottom {pct}% worldwide for {indicator}.'),

                                                          ('OWID_PERCENTILE_BEST', 'Best percentile (lower is better)',
                                                           'DEMOGRAPHICS', 'rank', 'RANKING', 0.85, 72, 0.83, true,
                                                           'This country is in the best {pct}% of nations globally when it comes to {indicator}.'),

                                                          ('OWID_PERCENTILE_WORST',
                                                           'Worst percentile (lower is better)', 'DEMOGRAPHICS', 'rank',
                                                           'RANKING', 0.82, 72, 0.80, true,
                                                           'For {indicator}, this country ranks among the worst {pct}% of nations in the world.'),

                                                          ('OWID_COMBO_GREEN_LEADER', 'Green energy leader combo',
                                                           'ENVIRONMENT', 'combo', 'UNIQUE_FEATURE', 0.88, 78, 0.86,
                                                           false,
                                                           'Renewables power {renewables}% of electricity here — this country is one of the world''s greenest grids, running almost entirely on clean energy.'),

                                                          ('OWID_COMBO_GREEN_DEMOCRACY', 'Green and democratic combo',
                                                           'ENVIRONMENT', 'combo', 'UNIQUE_FEATURE', 0.90, 80, 0.88,
                                                           false,
                                                           'This country pairs a {renewables}% renewable electricity share with a liberal democracy score of {demo}/1.0 — clean energy and political freedom combined.'),

                                                          ('OWID_COMBO_AUTOCRACY', 'Autocracy combo', 'POLITICS',
                                                           'combo', 'UNIQUE_FEATURE', 0.86, 80, 0.84, false,
                                                           'The liberal democracy index scores this country just {score} out of 1.0 — placing it firmly among the world''s most authoritarian regimes.'),

                                                          ('OWID_COMBO_POVERTY_AUTOCRACY',
                                                           'Poverty and autocracy combo', 'POLITICS', 'combo',
                                                           'UNIQUE_FEATURE', 0.88, 82, 0.86,
                                                           false,
                                                           'Extreme poverty affects {poverty}% of the population and the liberal democracy score is just {demo}/1.0 — this country faces both economic hardship and authoritarian governance.'),

                                                          ('OWID_CO2_HIGH', 'High CO₂ per capita', 'ENVIRONMENT',
                                                           'tonnes', 'RANKING', 0.90, 76, 0.88, false,
                                                           'This country emits {value} tonnes of CO₂ per person annually — ranking {rank} globally, one of the highest per-capita carbon footprints in the world.'),

                                                          ('OWID_CO2_LOW', 'Low CO₂ per capita', 'ENVIRONMENT',
                                                           'tonnes', 'ABSOLUTE_FACT', 0.84, 70, 0.82, false,
                                                           'Per-capita CO₂ emissions here are just {value} tonnes per year — one of the smallest carbon footprints globally.'),

                                                          ('OWID_CO2_FACT', 'CO₂ per capita fact', 'ENVIRONMENT',
                                                           'tonnes', 'ABSOLUTE_FACT', 0.86, 68, 0.84, false,
                                                           'This country emits {value} tonnes of CO₂ per person each year — ranking {rank} out of {total} countries globally.'),

                                                          ('OWID_CO2_VS_AVG', 'CO₂ vs global average', 'ENVIRONMENT',
                                                           'tonnes', 'RELATIVE_FACT', 0.84, 66, 0.82, false,
                                                           'Per-capita CO₂ emissions here are {value} tonnes — {diff} tonnes {direction} the global average of {avg}.'),

                                                          ('OWID_CO2_RANK', 'CO₂ global rank', 'ENVIRONMENT', 'tonnes',
                                                           'RANKING', 0.88, 72, 0.86, false,
                                                           'Ranked {rank} of {total} countries for per-capita CO₂ emissions — this country releases {value} tonnes per person per year.'),

                                                          ('OWID_LIFE_HIGH', 'High life expectancy', 'DEMOGRAPHICS',
                                                           'years', 'RANKING', 0.90, 66, 0.88, false,
                                                           'People born here can expect to live {value} years on average — placing this country among the world''s longest-lived nations at rank {rank}.'),

                                                          ('OWID_LIFE_LOW', 'Low life expectancy', 'DEMOGRAPHICS',
                                                           'years', 'RANKING', 0.86, 72, 0.84, false,
                                                           'Life expectancy at birth is just {value} years here — ranking {rank} out of {total} countries, among the world''s lowest.'),

                                                          ('OWID_LIFE_FACT', 'Life expectancy fact', 'DEMOGRAPHICS',
                                                           'years', 'ABSOLUTE_FACT', 0.84, 63, 0.82, false,
                                                           'The average life expectancy at birth here is {value} years — ranking {rank} out of {total} nations worldwide.'),

                                                          ('OWID_LIFE_VS_AVG', 'Life expectancy vs global average',
                                                           'DEMOGRAPHICS', 'years', 'RELATIVE_FACT', 0.82, 62, 0.80,
                                                           false,
                                                           'Life expectancy here is {value} years — {diff} years {direction} the global average of {avg}.'),

                                                          ('OWID_LIFE_RANK', 'Life expectancy global rank',
                                                           'DEMOGRAPHICS', 'years', 'RANKING', 0.88, 65, 0.86, false,
                                                           'Ranked {rank} of {total} countries for life expectancy at birth — residents here live an average of {value} years.'),

                                                          ('OWID_FERTILITY_HIGH', 'High fertility rate', 'DEMOGRAPHICS',
                                                           'children/woman', 'RANKING', 0.86, 72, 0.84, false,
                                                           'Women here have an average of {value} children — ranking {rank} globally for fertility rate, one of the highest in the world.'),

                                                          ('OWID_FERTILITY_LOW', 'Low fertility rate', 'DEMOGRAPHICS',
                                                           'children/woman', 'ABSOLUTE_FACT', 0.84, 68, 0.82, false,
                                                           'The fertility rate here is just {value} children per woman — well below the population replacement level.'),

                                                          ('OWID_FERTILITY_MID', 'Mid fertility rate', 'DEMOGRAPHICS',
                                                           'children/woman', 'RELATIVE_FACT', 0.80, 65, 0.78, false,
                                                           'With a fertility rate of {value} children per woman, this country is {direction} the global replacement rate of 2.1.'),

                                                          ('OWID_FERTILITY_RANK', 'Fertility rate global rank',
                                                           'DEMOGRAPHICS', 'children/woman', 'RANKING', 0.84, 68, 0.82,
                                                           false,
                                                           'Ranked {rank} out of {total} countries for fertility rate — women here have an average of {value} children.'),

                                                          ('OWID_INTERNET_HIGH', 'High internet penetration', 'CULTURE',
                                                           '%', 'RANKING', 0.84, 64, 0.82, false,
                                                           '{value}% of people here use the internet — ranking {rank} globally, one of the most connected populations in the world.'),

                                                          ('OWID_INTERNET_LOW', 'Low internet penetration', 'CULTURE',
                                                           '%', 'ABSOLUTE_FACT', 0.82, 70, 0.80, false,
                                                           'Only {value}% of the population here has internet access — one of the lowest connectivity rates in the world.'),

                                                          ('OWID_INTERNET_FACT', 'Internet penetration fact', 'CULTURE',
                                                           '%', 'ABSOLUTE_FACT', 0.78, 62, 0.76, false,
                                                           'Internet penetration stands at {value}% of the population here — a {tier} level of digital connectivity.'),

                                                          ('OWID_INTERNET_VS_AVG', 'Internet vs global average',
                                                           'CULTURE', '%', 'RELATIVE_FACT', 0.76, 60, 0.74, false,
                                                           'With {value}% of residents online, internet usage here is {direction} the global average of {avg}%.'),

                                                          ('OWID_ALCOHOL_HIGH', 'High alcohol consumption', 'HEALTH',
                                                           'litres/year', 'RANKING', 0.82, 72, 0.80, false,
                                                           'People here consume {value} litres of pure alcohol per person annually — ranking {rank} globally for per-capita alcohol intake.'),

                                                          ('OWID_ALCOHOL_LOW', 'Low alcohol consumption', 'HEALTH',
                                                           'litres/year', 'ABSOLUTE_FACT', 0.80, 68, 0.78, false,
                                                           'Per-capita alcohol consumption here is just {value} litres of pure alcohol per year — among the lowest in the world.'),

                                                          ('OWID_ALCOHOL_FACT', 'Alcohol consumption fact', 'HEALTH',
                                                           'litres/year', 'ABSOLUTE_FACT', 0.76, 65, 0.74, false,
                                                           'The average person here drinks {value} litres of pure alcohol annually — a {tier} consumption level globally.'),

                                                          ('OWID_ALCOHOL_VS_AVG', 'Alcohol vs global average', 'HEALTH',
                                                           'litres/year', 'RELATIVE_FACT', 0.76, 64, 0.74, false,
                                                           'At {value} litres of pure alcohol per person per year, consumption here is {direction} the global average of {avg} litres.'),

                                                          ('OWID_COMBO_HIGH_CARBON_FOSSIL',
                                                           'High carbon fossil economy combo', 'ENVIRONMENT', 'combo',
                                                           'UNIQUE_FEATURE', 0.88, 80, 0.86, false,
                                                           'Per-capita CO₂ emissions here reach {co2} tonnes per year while only {renewables}% of electricity comes from renewables — a fossil fuel-dependent economy with a heavy carbon footprint.'),

                                                          ('OWID_COMBO_LONG_HAPPY', 'Long-lived and happy combo',
                                                           'DEMOGRAPHICS', 'combo', 'UNIQUE_FEATURE', 0.86, 76, 0.84,
                                                           false,
                                                           'People here live an average of {life} years and rate their life satisfaction at {happiness}/10 — one of the rare combinations of longevity and happiness globally.'),

                                                          ('OWID_COMBO_FERTILITY_POVERTY',
                                                           'High fertility and poverty combo', 'DEMOGRAPHICS', 'combo',
                                                           'UNIQUE_FEATURE', 0.86, 78, 0.84, false,
                                                           'With a fertility rate of {fertility} children per woman and {poverty}% of the population in extreme poverty, this country faces significant demographic and economic pressures.'))
INSERT
INTO indicator_config (indicator_code, display_name, category, unit, hint_type, quality_score,
                       base_difficulty, weight, lower_is_better, hint_template, is_active)
SELECT indicator_code,
       display_name,
       category::VARCHAR,
       unit,
       hint_type::VARCHAR,
       quality_score,
       base_difficulty,
       weight,
       lower_is_better,
       hint_template,
       true
FROM owid_rows
ON CONFLICT (indicator_code) DO UPDATE
    SET display_name    = EXCLUDED.display_name,
        hint_template   = EXCLUDED.hint_template,
        base_difficulty = EXCLUDED.base_difficulty,
        quality_score   = EXCLUDED.quality_score,
        lower_is_better = EXCLUDED.lower_is_better,
        is_active       = true,
        updated_at      = CURRENT_TIMESTAMP;
