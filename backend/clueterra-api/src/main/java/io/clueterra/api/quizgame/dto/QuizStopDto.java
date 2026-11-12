package io.clueterra.api.quizgame.dto;

import java.util.List;

public record QuizStopDto(
        long stop,
        QuizCountryDto country,
        int level,
        int levels,
        String arrivalMode,
        String fromCountryCode,
        List<QuizQuestionDto> questions,
        QuizDestinationDto next,
        boolean finished,
        int answerXp) {
}
