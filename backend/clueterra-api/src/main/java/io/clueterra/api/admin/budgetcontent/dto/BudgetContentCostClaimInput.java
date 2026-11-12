package io.clueterra.api.admin.budgetcontent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetContentCostClaimInput(
        @NotBlank @Pattern(regexp = "^(REPORTED_COST|ORIGINAL_BUDGET|APPROVED_BUDGET|FINAL_COST|CURRENT_ESTIMATE|REVISED_BUDGET|EXPENDITURE_TO_DATE|FINANCING_AMOUNT|CONTRACT_VALUE)$") String costKind,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal sourceAmountLow,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal sourceAmountHigh,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String sourceCurrency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amountUsdLow,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amountUsdHigh,
        @Min(1850) @Max(2200) Integer costYear,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal unitsPerUsd,
        @NotNull LocalDate exchangeRateDate,
        @NotBlank @Pattern(regexp = "^(LATEST_REFERENCE_RATE|ANNUAL_AVERAGE|SOURCE_STATED)$") String exchangeRateBasis,
        @NotBlank @Pattern(regexp = "^(NONE|ECB|BUNDESBANK_BBK|WORLD_BANK)$") String exchangeRateProvider,
        @NotBlank @Pattern(regexp = "^(WIKIDATA|WIKIPEDIA|NASA|FTA|GAO|CLUETERRA_RESEARCH)$") String sourceCode,
        @NotBlank @Size(max = 200) String sourceName,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+") String sourceUrl,
        @Size(max = 2000) @Pattern(regexp = "^$|^https?://.+") String referenceUrl,
        @NotBlank @Size(max = 500) String normalizationNote,
        @Size(max = 500) String scopeNote,
        @NotBlank @Size(max = 120) String sourceKey,
        @Size(max = 1000) String evidenceText,
        @NotBlank @Pattern(regexp = "^(SOURCE_BACKED|CORROBORATED|DISPUTED)$") String verificationStatus,
        boolean primary,
        boolean answer) {
}
