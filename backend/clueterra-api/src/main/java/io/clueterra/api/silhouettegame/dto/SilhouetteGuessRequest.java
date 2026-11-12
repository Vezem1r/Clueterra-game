package io.clueterra.api.silhouettegame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SilhouetteGuessRequest(@NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode) {
}
