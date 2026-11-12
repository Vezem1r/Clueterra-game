package io.clueterra.budget.generation.challenge.model;

import java.time.LocalDate;
import java.util.List;

public record BudgetChallengeGenerationSummary(
        LocalDate fromDate,
        LocalDate toDate,
        int requestedDates,
        int createdChallenges,
        int skippedChallenges,
        List<LocalDate> createdDates) {

    public BudgetChallengeGenerationSummary {
        createdDates = List.copyOf(createdDates);
    }
}
