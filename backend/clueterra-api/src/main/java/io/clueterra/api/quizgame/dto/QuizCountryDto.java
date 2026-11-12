package io.clueterra.api.quizgame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record QuizCountryDto(
        String countryCode,
        @JsonRawValue String names,
        String region,
        String subregion,
        int tier) {
}
