package io.clueterra.api.quizgame.dto;

import java.util.List;

public record QuizSessionDto(
        QuizPlayerStatsDto stats,
        QuizStopDto current,
        List<QuizCountryLevelDto> countries,
        List<QuizVisitDto> trail,
        boolean complete) {
}
