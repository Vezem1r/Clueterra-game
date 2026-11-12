package io.clueterra.api.admin.budgetcontent.dto;

import io.clueterra.api.budget.dto.BudgetCategory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BudgetContentCandidateKey(
        @NotNull @Pattern(regexp = "^Q[1-9][0-9]*$") String wikidataId,
        @NotNull BudgetCategory category) {
}
