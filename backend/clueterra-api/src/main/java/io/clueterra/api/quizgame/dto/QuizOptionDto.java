package io.clueterra.api.quizgame.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizOptionDto(
        String type,
        String countryCode,
        @JsonRawValue String names,
        String flagUrl,
        String shape,
        Integer value,
        String text) {
}
