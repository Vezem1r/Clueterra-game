package io.clueterra.api.quizgame.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(@NotNull @Min(0) @Max(3) Integer option) {
}
