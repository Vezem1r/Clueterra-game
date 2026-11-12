package io.clueterra.api.rivergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RiverGuessRequest(@NotBlank @Size(max = 160) String answer) {
}
