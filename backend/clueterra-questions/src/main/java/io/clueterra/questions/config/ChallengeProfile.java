package io.clueterra.questions.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;

public record ChallengeProfile(
        ChallengeLevel level,
        Set<VisibilityTier> allowedCountryTiers,
        boolean easyEligibleOnly,
        Map<RegionGroup, RegionQuota> regionQuotas,
        Map<HintBand, Double> hintBandRatios,
        ImageQuotaRule imageQuotaRule,
        SlotDifficultyCurve difficultyCurve,
        int minDifficulty) {

    public ChallengeProfile {
        regionQuotas = regionQuotas.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(regionQuotas));
        hintBandRatios = hintBandRatios.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(hintBandRatios));
        allowedCountryTiers = Set.copyOf(allowedCountryTiers);
    }
}
