package io.clueterra.questions.controller.dto;

import jakarta.validation.constraints.Min;

public record RerollHintRequest(
        @Min(1) int countryOrder,
        @Min(1) int hintOrder) {
}
