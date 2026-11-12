package io.clueterra.api.admin.challenges.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateChallengeRequest(
        @NotBlank String challengeLevel,
        @NotBlank String difficulty
) {
}
