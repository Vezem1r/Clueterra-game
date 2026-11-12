package io.clueterra.api.quizgame.model;

import java.util.List;

import io.clueterra.api.quizgame.dto.QuizCountryDto;

public record QuizStopRow(
        long stop,
        QuizCountryDto country,
        int level,
        int levels,
        String arrivalMode,
        String fromCountryCode,
        QuizCountryDto next,
        String nextMode,
        List<String> nextRoute,
        int nextLevel,
        String questions,
        int answerXp) {
}
