package io.clueterra.questions.service.challenge;

import io.clueterra.questions.config.ChallengeProfile;

public record CountrySelectionStep(String description, ChallengeProfile profile, boolean applyCooldown) {
}
