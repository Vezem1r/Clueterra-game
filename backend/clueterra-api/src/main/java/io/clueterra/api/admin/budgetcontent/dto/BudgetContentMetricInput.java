package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetContentMetricInput(
        @NotBlank @Pattern(regexp = "^(LENGTH|MAIN_SPAN|HEIGHT|FLOOR_AREA|CAPACITY|POWER|ANNUAL_PASSENGERS|ANNUAL_CARGO_TONNES|CONTAINER_CAPACITY|STORAGE_VOLUME|MASS)$") String metricType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal normalizedValue,
        @NotBlank @Size(max = 40) String normalizedUnit,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal sourceValue,
        @NotBlank @Size(max = 40) String sourceUnit,
        @NotBlank @Pattern(regexp = "^(WIKIDATA|WIKIPEDIA|NASA|FTA|GAO|CLUETERRA_RESEARCH)$") String sourceCode,
        @NotBlank @Size(max = 200) String sourceName,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+") String sourceUrl,
        @Size(max = 2000) @Pattern(regexp = "^$|^https?://.+") String referenceUrl,
        @NotBlank @Size(max = 120) String sourceKey,
        @Size(max = 1000) String evidenceText,
        boolean primary) {
}
