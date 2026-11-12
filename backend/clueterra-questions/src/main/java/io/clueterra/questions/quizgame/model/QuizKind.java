package io.clueterra.questions.quizgame.model;

public enum QuizKind {
    FLAG(1, true),
    CAPITAL(2, true),
    NEIGHBOUR(2, true),
    CURRENCY(3, true),
    POPULATION(3, false),
    AREA(3, false),
    LEFT_DRIVING(4, false),
    DOMAIN(4, true),
    LANDLOCKED(4, false),
    OUTLINE(5, false),
    COASTLINE(5, false),
    TOURISTS(5, false),
    PEAK(5, false),
    NEIGHBOUR_CAPITAL(6, true),
    HIGHEST_POINT(6, true),
    NOT_NEIGHBOUR(6, true),
    LIFE_EXPECTANCY(7, false),
    WEALTH(7, false),
    HERITAGE(7, false),
    NEIGHBOUR_COUNT(7, true),
    FOREST(8, false),
    MEDIAN_AGE(8, false);

    private static final double TIER_WEIGHT = 1.0;
    private static final double REGIONAL_TIER_WEIGHT = 0.5;
    private static final int FIRST_TIER = 1;

    private final int baseScore;
    private final boolean aboutTheStop;

    QuizKind(int baseScore, boolean aboutTheStop) {
        this.baseScore = baseScore;
        this.aboutTheStop = aboutTheStop;
    }

    public double score(int tier) {
        return baseScore + (tier - FIRST_TIER) * (aboutTheStop ? TIER_WEIGHT : REGIONAL_TIER_WEIGHT);
    }
}
