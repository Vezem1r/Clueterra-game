package io.clueterra.api.admin.budgetcontent.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetContentMilestoneInput(
        @NotBlank @Pattern(regexp = "^(CONSTRUCTION_START|PLANNED_COMPLETION|CURRENT_COMPLETION_ESTIMATE|ACTUAL_COMPLETION|OPENING)$") String milestoneType,
        LocalDate milestoneDate,
        @Min(1850) @Max(2200) Integer milestoneYear,
        @NotBlank @Pattern(regexp = "^(DAY|YEAR)$") String datePrecision,
        @NotBlank @Pattern(regexp = "^(WIKIDATA|WIKIPEDIA|NASA|FTA|GAO|CLUETERRA_RESEARCH)$") String sourceCode,
        @NotBlank @Size(max = 200) String sourceName,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+") String sourceUrl,
        @Size(max = 2000) @Pattern(regexp = "^$|^https?://.+") String referenceUrl,
        @NotBlank @Size(max = 120) String sourceKey,
        @Size(max = 1000) String evidenceText,
        boolean primary) {
}
