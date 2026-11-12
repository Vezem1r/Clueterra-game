package io.clueterra.api.flaggame.model;

public record FlagGuessRow(boolean correct, int xpAwarded, int attempts, long questionId) {
}
