package io.clueterra.api.admin.flaggame.dto;

public record FlagQuestionSummaryDto(
        long activeQuestions,
        long capitalClues,
        long players,
        long solves,
        long guesses) {
}
