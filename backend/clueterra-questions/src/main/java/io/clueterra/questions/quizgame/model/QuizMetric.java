package io.clueterra.questions.quizgame.model;

public enum QuizMetric {
    POPULATION(QuizKind.POPULATION, true, 1.25, 0),
    AREA(QuizKind.AREA, true, 1.25, 0),
    PEAK(QuizKind.PEAK, true, 1.25, 0),
    COASTLINE(QuizKind.COASTLINE, true, 2.0, 0),
    TOURISTS(QuizKind.TOURISTS, true, 2.0, 0),
    LIFE_EXPECTANCY(QuizKind.LIFE_EXPECTANCY, true, 1.0, 3),
    WEALTH(QuizKind.WEALTH, true, 1.6, 0),
    HERITAGE(QuizKind.HERITAGE, true, 1.5, 2),
    FOREST(QuizKind.FOREST, true, 1.0, 12),
    MEDIAN_AGE(QuizKind.MEDIAN_AGE, false, 1.0, 5);

    private final QuizKind kind;
    private final boolean highestWins;
    private final double minRatio;
    private final double minGap;

    QuizMetric(QuizKind kind, boolean highestWins, double minRatio, double minGap) {
        this.kind = kind;
        this.highestWins = highestWins;
        this.minRatio = minRatio;
        this.minGap = minGap;
    }

    public QuizKind kind() {
        return kind;
    }

    public boolean highestWins() {
        return highestWins;
    }

    public boolean distinct(double first, double second) {
        double high = Math.max(first, second);
        double low = Math.min(first, second);
        return high - low >= minGap && high >= low * minRatio;
    }
}
