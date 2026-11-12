package io.clueterra.api.admin.budgetcontent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BudgetProjectSeoReferenceInput(
        @NotBlank
        @Size(max = 240)
        String name,

        @NotBlank
        @Size(max = 2000)
        String url,

        @NotBlank
        @Size(max = 500)
        String claim) {
}
