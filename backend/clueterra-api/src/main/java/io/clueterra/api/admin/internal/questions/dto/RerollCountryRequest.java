package io.clueterra.api.admin.internal.questions.dto;

import jakarta.validation.constraints.Min;

public record RerollCountryRequest(@Min(1) int countryOrder) {
}
