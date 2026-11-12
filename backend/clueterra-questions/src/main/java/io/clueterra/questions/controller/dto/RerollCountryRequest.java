package io.clueterra.questions.controller.dto;

import jakarta.validation.constraints.Min;

public record RerollCountryRequest(
        @Min(1) int countryOrder) {
}
