package io.clueterra.api.budget.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record BudgetHintRequest(
        @Positive(message = "Question variant ID must be positive") long variantId,
        @Min(value = 0, message = "Hint index must not be negative")
        @Max(value = 4, message = "Hint index is too large")
        int hintIndex) {
}
