package io.clueterra.questions.clients.hydrorivers;

import java.util.Arrays;
import java.util.Optional;

public enum HydroRiverSkeletonVariant {

    EASY(1, "easy", 24),
    MEDIUM(2, "medium", 42),
    HARD(3, "hard", 60);

    private static final String INDICATOR_PREFIX = "HYDRORIVERS_RIVER_SKELETON_";

    private final int indicatorNumber;
    private final String assetSuffix;
    private final int defaultBaseDifficulty;

    HydroRiverSkeletonVariant(int indicatorNumber, String assetSuffix, int defaultBaseDifficulty) {
        this.indicatorNumber = indicatorNumber;
        this.assetSuffix = assetSuffix;
        this.defaultBaseDifficulty = defaultBaseDifficulty;
    }

    public String indicatorCode() {
        return INDICATOR_PREFIX + indicatorNumber;
    }

    public String assetSuffix() {
        return assetSuffix;
    }

    public int defaultBaseDifficulty() {
        return defaultBaseDifficulty;
    }

    public static String indicatorPrefix() {
        return INDICATOR_PREFIX;
    }

    public static Optional<HydroRiverSkeletonVariant> fromIndicator(String indicatorCode) {
        if (indicatorCode == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(variant -> variant.indicatorCode().equals(indicatorCode))
                .findFirst();
    }
}
