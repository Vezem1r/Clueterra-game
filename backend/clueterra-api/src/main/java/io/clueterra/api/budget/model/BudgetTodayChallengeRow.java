package io.clueterra.api.budget.model;

import java.time.LocalDate;

public record BudgetTodayChallengeRow(LocalDate date, int questionCount) {
}
