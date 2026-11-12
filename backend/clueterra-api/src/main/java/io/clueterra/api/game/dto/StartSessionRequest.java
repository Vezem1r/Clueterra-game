package io.clueterra.api.game.dto;

import io.clueterra.api.common.enums.ChallengeLevel;
import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(
        @NotNull(message = "Challenge level is required") ChallengeLevel level) {
}
