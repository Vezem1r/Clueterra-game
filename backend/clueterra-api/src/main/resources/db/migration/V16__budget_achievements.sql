INSERT INTO cl_api_schema.badge_categories (id, name, display_order)
VALUES (700000000000000001, 'BudgetGuessr', 40)
ON CONFLICT (name) DO UPDATE SET
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

INSERT INTO cl_api_schema.badges (
    id, slug, image_url, is_secret, is_active, grant_type, grant_config,
    display_order, accent_color_key, effect_color_key, category_id
)
VALUES
    (700000000000000101, 'first-budget', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_CHALLENGES_COMPLETED","threshold":1}'::JSONB,
     401, 'emerald-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000102, 'perfect-budget-guess', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_EXACT_GUESSES","threshold":1}'::JSONB,
     402, 'amber-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000103, 'budget-clean-sweep', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_CLEAN_SWEEPS","threshold":1}'::JSONB,
     403, 'cyan-core', 'cyan-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000104, 'budget-all-categories', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_ALL_CATEGORIES","threshold":"ALL"}'::JSONB,
     404, 'violet-core', 'violet-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000105, 'budget-50-projects', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_UNIQUE_PROJECTS","threshold":50}'::JSONB,
     405, 'sky-core', 'sky-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000106, 'budget-100-projects', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_UNIQUE_PROJECTS","threshold":100}'::JSONB,
     406, 'indigo-core', 'indigo-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000107, 'budget-250-projects', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_UNIQUE_PROJECTS","threshold":250}'::JSONB,
     407, 'violet-core', 'violet-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000108, 'higher-lower-streak', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_BEST_HIGHER_LOWER_STREAK","threshold":5}'::JSONB,
     408, 'rose-core', 'rose-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000109, 'budget-daily-dedication', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_CHALLENGES_COMPLETED","threshold":50}'::JSONB,
     409, 'amber-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000110, 'higher-lower-master', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_HIGHER_LOWER_CORRECT","threshold":100}'::JSONB,
     410, 'rose-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000111, 'cost-engineer', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_COST_PER_UNIT_CORRECT","threshold":50}'::JSONB,
     411, 'cyan-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000112, 'budget-around-the-world', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_DISTINCT_COUNTRIES","threshold":25}'::JSONB,
     412, 'sky-core', 'emerald-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000113, 'big-spender', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_MASTERED_VALUE_USD","threshold":100000000000}'::JSONB,
     413, 'amber-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000114, 'trillion-club', NULL, FALSE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_MASTERED_VALUE_USD","threshold":1000000000000}'::JSONB,
     414, 'violet-core', 'amber-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr')),
    (700000000000000115, 'budget-black-hole', NULL, TRUE, TRUE, 'TEMPLATE',
     '{"trigger":"BUDGET_SEVERE_MISSES","threshold":10}'::JSONB,
     415, 'rose-core', 'rose-glow', (SELECT id FROM cl_api_schema.badge_categories WHERE name = 'BudgetGuessr'))
ON CONFLICT (slug) DO UPDATE SET
    is_secret = EXCLUDED.is_secret,
    is_active = TRUE,
    grant_type = EXCLUDED.grant_type,
    grant_config = EXCLUDED.grant_config,
    display_order = EXCLUDED.display_order,
    accent_color_key = EXCLUDED.accent_color_key,
    effect_color_key = EXCLUDED.effect_color_key,
    category_id = EXCLUDED.category_id,
    updated_at = NOW();

WITH translations(translation_id, slug, locale, name, unlocked, locked) AS (
    VALUES
    (700000000000001011, 'first-budget', 'en', 'First Budget', 'Completed your first BudgetGuessr challenge.', 'Complete a BudgetGuessr challenge.'),
    (700000000000001012, 'first-budget', 'ru', 'Первый бюджет', 'Первый челлендж BudgetGuessr завершён.', 'Завершите челлендж BudgetGuessr.'),
    (700000000000001013, 'first-budget', 'cs', 'První rozpočet', 'Dokončili jste první výzvu BudgetGuessr.', 'Dokončete výzvu BudgetGuessr.'),
    (700000000000001021, 'perfect-budget-guess', 'en', 'Perfect Guess', 'Hit a project cost with 100% accuracy.', 'Guess one project cost with 100% accuracy.'),
    (700000000000001022, 'perfect-budget-guess', 'ru', 'Точный расчёт', 'Стоимость проекта угадана со 100% точностью.', 'Угадайте стоимость проекта со 100% точностью.'),
    (700000000000001023, 'perfect-budget-guess', 'cs', 'Přesný odhad', 'Odhadli jste cenu projektu se 100% přesností.', 'Odhadněte cenu projektu se 100% přesností.'),
    (700000000000001031, 'budget-clean-sweep', 'en', 'Clean Sweep', 'Earned full XP on every question in one challenge.', 'Earn full XP on every question in one challenge.'),
    (700000000000001032, 'budget-clean-sweep', 'ru', 'Чистая победа', 'Получен полный XP за каждый вопрос челленджа.', 'Получите полный XP за каждый вопрос одного челленджа.'),
    (700000000000001033, 'budget-clean-sweep', 'cs', 'Čisté vítězství', 'Získali jste plné XP za každou otázku jedné výzvy.', 'Získejte plné XP za každou otázku jedné výzvy.'),
    (700000000000001041, 'budget-all-categories', 'en', 'All Categories', 'Mastered a project in every available category.', 'Master one project in every available category.'),
    (700000000000001042, 'budget-all-categories', 'ru', 'Все категории', 'Освоен проект в каждой доступной категории.', 'Освойте проект в каждой доступной категории.'),
    (700000000000001043, 'budget-all-categories', 'cs', 'Všechny kategorie', 'Zvládli jste projekt v každé dostupné kategorii.', 'Zvládněte projekt v každé dostupné kategorii.'),
    (700000000000001051, 'budget-50-projects', 'en', '50 Projects', 'Explored 50 unique projects.', 'Explore 50 unique projects.'),
    (700000000000001052, 'budget-50-projects', 'ru', '50 проектов', 'Исследовано 50 уникальных проектов.', 'Исследуйте 50 уникальных проектов.'),
    (700000000000001053, 'budget-50-projects', 'cs', '50 projektů', 'Prozkoumali jste 50 unikátních projektů.', 'Prozkoumejte 50 unikátních projektů.'),
    (700000000000001061, 'budget-100-projects', 'en', '100 Projects', 'Explored 100 unique projects.', 'Explore 100 unique projects.'),
    (700000000000001062, 'budget-100-projects', 'ru', '100 проектов', 'Исследовано 100 уникальных проектов.', 'Исследуйте 100 уникальных проектов.'),
    (700000000000001063, 'budget-100-projects', 'cs', '100 projektů', 'Prozkoumali jste 100 unikátních projektů.', 'Prozkoumejte 100 unikátních projektů.'),
    (700000000000001071, 'budget-250-projects', 'en', '250 Projects', 'Explored 250 unique projects.', 'Explore 250 unique projects.'),
    (700000000000001072, 'budget-250-projects', 'ru', '250 проектов', 'Исследовано 250 уникальных проектов.', 'Исследуйте 250 уникальных проектов.'),
    (700000000000001073, 'budget-250-projects', 'cs', '250 projektů', 'Prozkoumali jste 250 unikátních projektů.', 'Prozkoumejte 250 unikátních projektů.'),
    (700000000000001081, 'higher-lower-streak', 'en', 'Higher / Lower Streak', 'Won five Higher / Lower questions in a row.', 'Win five Higher / Lower questions in a row.'),
    (700000000000001082, 'higher-lower-streak', 'ru', 'Серия Больше / Меньше', 'Пять правильных ответов Больше / Меньше подряд.', 'Ответьте правильно пять раз подряд в Больше / Меньше.'),
    (700000000000001083, 'higher-lower-streak', 'cs', 'Série Více / Méně', 'Pět správných odpovědí Více / Méně v řadě.', 'Odpovězte správně pětkrát v řadě ve Více / Méně.'),
    (700000000000001091, 'budget-daily-dedication', 'en', 'Daily Dedication', 'Completed 50 BudgetGuessr challenges.', 'Complete 50 BudgetGuessr challenges.'),
    (700000000000001092, 'budget-daily-dedication', 'ru', 'Ежедневная преданность', 'Завершено 50 челленджей BudgetGuessr.', 'Завершите 50 челленджей BudgetGuessr.'),
    (700000000000001093, 'budget-daily-dedication', 'cs', 'Denní odhodlání', 'Dokončili jste 50 výzev BudgetGuessr.', 'Dokončete 50 výzev BudgetGuessr.'),
    (700000000000001101, 'higher-lower-master', 'en', 'Higher / Lower Master', 'Answered 100 Higher / Lower questions correctly.', 'Answer 100 Higher / Lower questions correctly.'),
    (700000000000001102, 'higher-lower-master', 'ru', 'Мастер Больше / Меньше', 'Дано 100 правильных ответов в Больше / Меньше.', 'Дайте 100 правильных ответов в Больше / Меньше.'),
    (700000000000001103, 'higher-lower-master', 'cs', 'Mistr Více / Méně', 'Správně jste odpověděli na 100 otázek Více / Méně.', 'Odpovězte správně na 100 otázek Více / Méně.'),
    (700000000000001111, 'cost-engineer', 'en', 'Cost Engineer', 'Solved 50 Cost per Unit questions for full XP.', 'Solve 50 Cost per Unit questions for full XP.'),
    (700000000000001112, 'cost-engineer', 'ru', 'Инженер стоимости', 'Решено 50 вопросов Стоимость за единицу на полный XP.', 'Решите 50 вопросов Стоимость за единицу на полный XP.'),
    (700000000000001113, 'cost-engineer', 'cs', 'Nákladový inženýr', 'Vyřešili jste 50 otázek Cena za jednotku za plné XP.', 'Vyřešte 50 otázek Cena za jednotku za plné XP.'),
    (700000000000001121, 'budget-around-the-world', 'en', 'Around the World', 'Mastered projects in 25 countries.', 'Master projects in 25 countries.'),
    (700000000000001122, 'budget-around-the-world', 'ru', 'Вокруг света', 'Освоены проекты в 25 странах.', 'Освойте проекты в 25 странах.'),
    (700000000000001123, 'budget-around-the-world', 'cs', 'Kolem světa', 'Zvládli jste projekty ve 25 zemích.', 'Zvládněte projekty ve 25 zemích.'),
    (700000000000001131, 'big-spender', 'en', 'Big Spender', 'Mastered projects worth a combined $100 billion.', 'Master projects worth a combined $100 billion.'),
    (700000000000001132, 'big-spender', 'ru', 'Большой бюджет', 'Общая стоимость освоенных проектов достигла $100 млрд.', 'Освойте проекты общей стоимостью $100 млрд.'),
    (700000000000001133, 'big-spender', 'cs', 'Velký rozpočet', 'Hodnota zvládnutých projektů dosáhla 100 miliard dolarů.', 'Zvládněte projekty za celkem 100 miliard dolarů.'),
    (700000000000001141, 'trillion-club', 'en', 'Trillion Club', 'Mastered projects worth a combined $1 trillion.', 'Master projects worth a combined $1 trillion.'),
    (700000000000001142, 'trillion-club', 'ru', 'Клуб триллиона', 'Общая стоимость освоенных проектов достигла $1 трлн.', 'Освойте проекты общей стоимостью $1 трлн.'),
    (700000000000001143, 'trillion-club', 'cs', 'Klub bilionu', 'Hodnota zvládnutých projektů dosáhla jednoho bilionu dolarů.', 'Zvládněte projekty za celkem jeden bilion dolarů.'),
    (700000000000001151, 'budget-black-hole', 'en', 'Budget Black Hole', 'Missed the real cost by a spectacular margin ten times.', 'Some budgets disappear without a trace.'),
    (700000000000001152, 'budget-black-hole', 'ru', 'Бюджетная чёрная дыра', 'Десять раз стоимость проекта оказалась почти в другой вселенной.', 'Некоторые бюджеты исчезают бесследно.'),
    (700000000000001153, 'budget-black-hole', 'cs', 'Rozpočtová černá díra', 'Desetkrát jste minuli skutečnou cenu opravdu velkolepě.', 'Některé rozpočty zmizí beze stopy.')
)
INSERT INTO cl_api_schema.badge_translations (
    id, badge_id, locale, name, description_unlocked, description_locked
)
SELECT
    translations.translation_id,
    badge.id,
    translations.locale,
    translations.name,
    translations.unlocked,
    translations.locked
FROM translations
JOIN cl_api_schema.badges badge ON badge.slug = translations.slug
ON CONFLICT (badge_id, locale) DO UPDATE SET
    name = EXCLUDED.name,
    description_unlocked = EXCLUDED.description_unlocked,
    description_locked = EXCLUDED.description_locked;
