package io.clueterra.api.budget.dto;

import java.math.BigDecimal;

import io.clueterra.api.budget.model.BudgetAnswerChoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

public record BudgetAnswerRequest(
        @Positive(message = "Question variant ID must be positive") long variantId,
        @DecimalMin(value = "0", message = "Guess must not be negative")
        @Digits(integer = 18, fraction = 6, message = "Guess has too many digits")
        BigDecimal value,
        BudgetAnswerChoice choice) {
}
