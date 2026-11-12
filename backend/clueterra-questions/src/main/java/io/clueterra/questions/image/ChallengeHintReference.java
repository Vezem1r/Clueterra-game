package io.clueterra.questions.image;

public record ChallengeHintReference(
        Long challengeId,
        int countryOrder,
        int hintOrder) {
}
