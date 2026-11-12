package io.clueterra.budget.generation.challenge.model;

import java.time.LocalDate;

public record BudgetChallengePublication(long challengeId, LocalDate challengeDate, boolean created) {
}
