package io.clueterra.questions.controller.dto;

public record ChallengeRerollResponse(
        Long challengeId,
        String status,
        int totalCountries,
        int totalHints) {
}
