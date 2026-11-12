package io.clueterra.api.budget.dto;

import java.time.LocalDate;

public record BudgetTodayChallengeDto(LocalDate date, int questionCount, boolean available) {
}
