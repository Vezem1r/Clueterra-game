package io.clueterra.questions.service.challenge;

import java.util.Map;

import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.entity.enums.HintBand;

public record ChallengePlan(
        ChallengeProfile profile,
        int countriesCount,
        int hintsPerCountry,
        int totalSlots,
        Map<HintBand, Integer> bandQuotas,
        int requiredImageHints,
        int[] slotDifficultyTargets,
        HintBand[][] slotBandTargets,
        int minDifficulty) {
}
