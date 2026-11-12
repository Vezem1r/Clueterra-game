package io.clueterra.api.admin.internal.questions.dto;

import jakarta.validation.constraints.Min;

public record RerollHintRequest(@Min(1) int countryOrder, @Min(1) int hintOrder) {
}
