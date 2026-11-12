package io.clueterra.api.admin.budgetcontent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BudgetProjectSeoFactInput(
        @NotBlank
        @Size(max = 120)
        String label,

        @NotBlank
        @Size(max = 500)
        String value,

        @NotBlank
        @Size(max = 240)
        String sourceName,

        @NotBlank
        @Size(max = 2000)
        String sourceUrl) {
}
