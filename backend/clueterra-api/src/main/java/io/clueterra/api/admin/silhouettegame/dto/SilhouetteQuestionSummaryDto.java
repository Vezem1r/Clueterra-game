package io.clueterra.api.admin.silhouettegame.dto;

public record SilhouetteQuestionSummaryDto(
        long activeQuestions,
        long landmarkClues,
        long players,
        long solves,
        long guesses) {
}
