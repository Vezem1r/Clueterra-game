package io.clueterra.api.game.dto;

import jakarta.validation.constraints.NotNull;

public record GuessRequest(@NotNull(message = "Country code is required") String countryCode) {
}
