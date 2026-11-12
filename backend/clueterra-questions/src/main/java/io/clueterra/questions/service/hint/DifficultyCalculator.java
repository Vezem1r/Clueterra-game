package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.service.hint.lens.StatisticContext;

import java.util.Map;
import java.util.Set;

public final class DifficultyCalculator {

    private DifficultyCalculator() {
    }

    private static final Set<String> SUPER_FAMOUS_COUNTRIES = Set.of(
            "US", "CN", "RU", "GB", "FR", "DE", "JP", "IN", "BR", "CA"
    );

    private static final Set<String> FAMOUS_COUNTRIES = Set.of(
            "AU", "IT", "ES", "MX", "KR", "NL", "CH", "SE", "PL", "AR",
            "EG", "ZA", "TR", "SA", "AE", "TH", "VN", "ID", "PH", "MY",
            "NG", "KE", "PK", "BD", "IR", "IQ", "IL", "GR", "PT", "NO"
    );

    private static final Set<String> MODERATELY_KNOWN_COUNTRIES = Set.of(
            "SG", "NZ", "IE", "AT", "BE", "DK", "FI", "CZ", "HU", "RO",
            "UA", "CO", "CL", "PE", "VE", "CU", "MA", "TN", "GH", "TZ"
    );

    private static final Map<Category, Integer> CATEGORY_MODIFIERS = Map.ofEntries(
            Map.entry(Category.GEOGRAPHY, -3),
            Map.entry(Category.DEMOGRAPHICS, 0),
            Map.entry(Category.ECONOMY, 3),
            Map.entry(Category.CULTURE, -2),
            Map.entry(Category.SPORTS, -4),
            Map.entry(Category.INFRASTRUCTURE, 5),
            Map.entry(Category.HEALTH, 4),
            Map.entry(Category.EDUCATION, 5),
            Map.entry(Category.ENVIRONMENT, 3),
            Map.entry(Category.POLITICS, 2),
            Map.entry(Category.HISTORY, 0),
            Map.entry(Category.FOOD, -3),
            Map.entry(Category.LANDMARKS, -5),
            Map.entry(Category.TRIVIA, 8),
            Map.entry(Category.CLIMATE, 2),
            Map.entry(Category.NATURE, 0),
            Map.entry(Category.GENERAL, 0)
    );

    private static final int MIN_DIFFICULTY = 5;
    private static final int MAX_DIFFICULTY = 100;
    private static final int MAX_NEGATIVE_MODIFIER = -25;
    private static final int MAX_POSITIVE_MODIFIER = 35;

    public static int calculate(StatisticContext ctx, int baseDifficulty) {
        int totalModifier = 0;

        totalModifier += getCountryModifier(ctx.countryCode());
        totalModifier += getIndicatorModifier(ctx.statistic().getIndicatorCode());
        totalModifier += getRankModifier(ctx);
        totalModifier += getCategoryModifier(ctx.statistic().getCategory());
        totalModifier += getAmbiguityModifier(ctx);
        totalModifier += getDataQualityModifier(ctx);

        totalModifier = Math.max(MAX_NEGATIVE_MODIFIER, Math.min(MAX_POSITIVE_MODIFIER, totalModifier));

        int difficulty = baseDifficulty + totalModifier;
        return clamp(difficulty);
    }

    public static int calculateForRanking(StatisticContext ctx, int rank, int totalCountries) {
        int base = getRankBaseDifficulty(rank, totalCountries);
        return calculate(ctx, base);
    }

    public static int calculateForPercentile(StatisticContext ctx, double percentile) {
        int base = getPercentileBaseDifficulty(percentile);
        return calculate(ctx, base);
    }

    public static int calculateForSuperlative(StatisticContext ctx) {
        int base = IndicatorConfig.isHighProfile(ctx.statistic().getIndicatorCode()) ? 6 : 12;
        return calculate(ctx, base);
    }

    public static int calculateForAbsoluteFact(StatisticContext ctx, boolean isNotable, int configBaseDifficulty) {
        int base = isNotable ? Math.max(configBaseDifficulty - 12, 10) : configBaseDifficulty;
        return calculate(ctx, base);
    }

    public static int calculateForTextFact(StatisticContext ctx, int baseDifficulty, boolean isUnique) {
        int base = isUnique ? Math.max(baseDifficulty - 10, 5) : baseDifficulty;
        return calculate(ctx, base);
    }

    private static int getCountryModifier(String countryCode) {
        if (countryCode == null) return 0;
        if (SUPER_FAMOUS_COUNTRIES.contains(countryCode)) return -10;
        if (FAMOUS_COUNTRIES.contains(countryCode)) return -5;
        if (MODERATELY_KNOWN_COUNTRIES.contains(countryCode)) return 0;
        return 8;
    }

    private static int getIndicatorModifier(String indicatorCode) {
        if (IndicatorConfig.isHighProfile(indicatorCode)) return -8;
        if (IndicatorConfig.isMediumProfile(indicatorCode)) return -3;
        if (IndicatorConfig.isHighInterest(indicatorCode)) return -5;
        return 3;
    }

    private static int getRankModifier(StatisticContext ctx) {
        if (!ctx.hasGlobalRank()) return 0;

        int rank = ctx.globalRank();
        int total = ctx.totalCountriesWithData();

        if (rank == 1) return -5;
        if (rank <= 3) return -3;
        if (rank <= 5) return -1;
        if (rank <= 10) return 2;
        if (rank <= 20) return 5;

        double relativeRank = (double) rank / total;
        if (relativeRank <= 0.5) return 8;
        if (relativeRank <= 0.8) return 10;
        return 6;
    }

    private static int getCategoryModifier(Category category) {
        if (category == null) return 0;
        return CATEGORY_MODIFIERS.getOrDefault(category, 0);
    }

    private static int getAmbiguityModifier(StatisticContext ctx) {
        if (!ctx.hasGlobalRank() && !ctx.hasPercentile()) return 5;

        int ambiguity = estimateAmbiguity(ctx);
        if (ambiguity == 1) return -5;
        if (ambiguity <= 3) return -3;
        if (ambiguity <= 5) return -1;
        if (ambiguity <= 10) return 2;
        if (ambiguity <= 20) return 5;
        if (ambiguity <= 50) return 8;
        return 10;
    }

    private static int estimateAmbiguity(StatisticContext ctx) {
        if (ctx.hasGlobalRank()) {
            int rank = ctx.globalRank();
            if (rank == 1) return 1;
            if (rank <= 3) return rank;
            if (rank <= 5) return 5;
            if (rank <= 10) return 10;
            if (rank <= 20) return 20;
            return Math.min(50, ctx.totalCountriesWithData() / 4);
        }

        if (ctx.hasPercentile()) {
            double p = ctx.percentile();
            int total = ctx.totalCountriesWithData();
            if (p >= 99 || p <= 1) return Math.max(1, total / 100);
            if (p >= 95 || p <= 5) return Math.max(2, total / 20);
            if (p >= 90 || p <= 10) return Math.max(5, total / 10);
            if (p >= 75 || p <= 25) return Math.max(10, total / 4);
            return total / 2;
        }

        return 50;
    }

    private static int getDataQualityModifier(StatisticContext ctx) {
        int modifier = 0;

        if (ctx.isDataSparse()) modifier += 5;
        if (ctx.confidence() < 0.5) modifier += 5;
        else if (ctx.confidence() < 0.7) modifier += 2;

        modifier += getWeightModifier(ctx.weight());

        return modifier;
    }

    private static int getWeightModifier(double weight) {
        if (weight >= 0.90) return -5;
        if (weight >= 0.75) return -3;
        if (weight >= 0.60) return 0;
        if (weight >= 0.45) return 2;
        if (weight >= 0.30) return 5;
        return 8;
    }

    private static int getRankBaseDifficulty(int rank, int totalCountries) {
        if (rank == 1) return 12;
        if (rank == 2) return 18;
        if (rank == 3) return 24;
        if (rank <= 5) return 32;
        if (rank <= 10) return 42;
        if (rank <= 20) return 52;

        double relativePosition = (double) rank / totalCountries;
        if (relativePosition > 0.9) return 48;
        if (relativePosition > 0.5) return 62;
        return 55;
    }

    private static int getPercentileBaseDifficulty(double percentile) {
        if (percentile >= 99 || percentile <= 1) return 18;
        if (percentile >= 95 || percentile <= 5) return 28;
        if (percentile >= 90 || percentile <= 10) return 38;
        if (percentile >= 80 || percentile <= 20) return 48;
        if (percentile >= 75 || percentile <= 25) return 55;
        return 68;
    }

    private static int clamp(int difficulty) {
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, difficulty));
    }
}
