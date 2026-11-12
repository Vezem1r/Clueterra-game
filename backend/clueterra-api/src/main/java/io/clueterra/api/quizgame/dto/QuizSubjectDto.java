package io.clueterra.api.quizgame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record QuizSubjectDto(String countryCode, @JsonRawValue String names) {
}
