package io.clueterra.api.silhouettegame.model;

public record SilhouetteGuessRow(boolean correct, int xpAwarded, int attempts, long questionId) {
}
