package io.clueterra.api.flaggame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FlagGuessRequest(@NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode) {
}
