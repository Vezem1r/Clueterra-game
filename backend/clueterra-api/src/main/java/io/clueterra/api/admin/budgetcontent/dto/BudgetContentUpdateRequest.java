package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetContentUpdateRequest(
        @NotBlank @Size(max = 300) String name,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @NotBlank @Size(max = 120) String countryName,
        @NotBlank @Pattern(regexp = "^(AFRICA|ASIA|EUROPE|NORTH_AMERICA|SOUTH_AMERICA|OCEANIA|UNKNOWN)$") String regionCode,
        @Min(1850) @Max(2200) Integer projectYear,
        @NotBlank @Pattern(regexp = "^(EASY|MEDIUM|HARD)$") String difficulty,
        @NotBlank @Size(max = 500) String imageFileTitle,
        @Valid BudgetContentDescriptionSourceInput descriptionSource,
        @NotEmpty @Size(max = 50) List<@Valid BudgetContentCostClaimInput> costClaims,
        @NotNull @Size(max = 50) List<@Valid BudgetContentMetricInput> metrics,
        @NotNull @Size(max = 50) List<@Valid BudgetContentMilestoneInput> milestones) {
}
