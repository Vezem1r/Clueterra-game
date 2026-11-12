package io.clueterra.api.admin.budgetcontent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetContentDescriptionSourceInput(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+") String url,
        @NotBlank @Size(max = 100) String licenseLabel,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+") String licenseUrl) {
}
