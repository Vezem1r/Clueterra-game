package io.clueterra.questions.config;

import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;

public record CountryVisibilityProfile(
        double score,
        VisibilityTier visibilityTier,
        boolean easyEligible,
        RegionGroup regionGroup) {
}
