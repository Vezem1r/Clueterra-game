package io.clueterra.api.quizgame.dto;

import java.util.List;

public record QuizQuestionDto(
        int position,
        String kind,
        QuizSubjectDto subject,
        List<QuizOptionDto> options,
        QuizAnswerResultDto answer) {
}
