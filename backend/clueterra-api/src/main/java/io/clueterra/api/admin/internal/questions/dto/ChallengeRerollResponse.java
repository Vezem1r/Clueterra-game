package io.clueterra.api.admin.internal.questions.dto;

public record ChallengeRerollResponse(Long challengeId, String status, int totalCountries, int totalHints) {
}
