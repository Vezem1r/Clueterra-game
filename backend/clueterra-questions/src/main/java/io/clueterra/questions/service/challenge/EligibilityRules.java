package io.clueterra.questions.service.challenge;

import io.clueterra.questions.entity.enums.HintBand;

public record EligibilityRules(
        HintBand targetBand,
        boolean enforceFamily,
        boolean enforceTextFingerprint,
        boolean enforceDataSourceCap) {
}
