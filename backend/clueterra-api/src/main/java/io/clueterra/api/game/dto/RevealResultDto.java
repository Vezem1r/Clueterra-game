package io.clueterra.api.game.dto;

public record RevealResultDto(HintDto hint, int hintsRevealed, int attemptsRemaining) {
}
