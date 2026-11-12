package io.clueterra.questions.config;

import java.util.Map;
import java.util.Set;

import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;

public final class ChallengeProfileFactory {

    private ChallengeProfileFactory() {
    }

    public static ChallengeProfile forLevel(ChallengeLevel level) {
        return switch (level) {
            case EASY -> buildEasy();
            case MEDIUM -> buildMedium();
            case HARD -> buildHard();
        };
    }

    public static ChallengeProfile expandedForLevel(ChallengeLevel level) {
        return switch (level) {
            case EASY -> buildEasyExpanded();
            case MEDIUM -> buildMedium();
            case HARD -> buildHard();
        };
    }

    private static ChallengeProfile buildEasy() {
        return new ChallengeProfile(
                ChallengeLevel.EASY,
                Set.of(VisibilityTier.EASY, VisibilityTier.MEDIUM),
                true,
                Map.of(
                        RegionGroup.EUROPE, new RegionQuota(1, 2),
                        RegionGroup.AMERICAS, new RegionQuota(1, 2),
                        RegionGroup.ASIA, new RegionQuota(1, 2),
                        RegionGroup.AFRICA, new RegionQuota(0, 1),
                        RegionGroup.OCEANIA, new RegionQuota(0, 1)),
                Map.of(HintBand.EASY_HINT, 1.0),
                new ImageQuotaRule(),
                new SlotDifficultyCurve(32, 5),
                0);
    }

    private static ChallengeProfile buildEasyExpanded() {
        return new ChallengeProfile(
                ChallengeLevel.EASY,
                Set.of(VisibilityTier.EASY, VisibilityTier.MEDIUM),
                false,
                Map.of(
                        RegionGroup.EUROPE, new RegionQuota(0, 2),
                        RegionGroup.AMERICAS, new RegionQuota(0, 2),
                        RegionGroup.ASIA, new RegionQuota(0, 2),
                        RegionGroup.AFRICA, new RegionQuota(0, 1),
                        RegionGroup.OCEANIA, new RegionQuota(0, 1)),
                Map.of(HintBand.EASY_HINT, 0.7, HintBand.MEDIUM_HINT, 0.3),
                new ImageQuotaRule(),
                new SlotDifficultyCurve(50, 5),
                0);
    }

    private static ChallengeProfile buildMedium() {
        return new ChallengeProfile(
                ChallengeLevel.MEDIUM,
                Set.of(VisibilityTier.EASY, VisibilityTier.MEDIUM, VisibilityTier.HARD),
                false,
                Map.of(
                        RegionGroup.EUROPE, new RegionQuota(1, 2),
                        RegionGroup.AMERICAS, new RegionQuota(1, 2),
                        RegionGroup.ASIA, new RegionQuota(1, 2),
                        RegionGroup.AFRICA, new RegionQuota(0, 1),
                        RegionGroup.OCEANIA, new RegionQuota(0, 1)),
                Map.of(
                        HintBand.EASY_HINT, 0.4,
                        HintBand.MEDIUM_HINT, 0.4,
                        HintBand.HARD_HINT, 0.2),
                new ImageQuotaRule(),
                new SlotDifficultyCurve(75, 10),
                15);
    }

    private static ChallengeProfile buildHard() {
        return new ChallengeProfile(
                ChallengeLevel.HARD,
                Set.of(VisibilityTier.MEDIUM, VisibilityTier.HARD, VisibilityTier.EXPERT),
                false,
                Map.of(
                        RegionGroup.EUROPE, new RegionQuota(0, 2),
                        RegionGroup.AMERICAS, new RegionQuota(0, 2),
                        RegionGroup.ASIA, new RegionQuota(0, 2),
                        RegionGroup.AFRICA, new RegionQuota(0, 2),
                        RegionGroup.OCEANIA, new RegionQuota(0, 1),
                        RegionGroup.MENA, new RegionQuota(0, 1)),
                Map.of(HintBand.MEDIUM_HINT, 0.4, HintBand.HARD_HINT, 0.6),
                new ImageQuotaRule(),
                new SlotDifficultyCurve(98, 45),
                40);
    }
}
