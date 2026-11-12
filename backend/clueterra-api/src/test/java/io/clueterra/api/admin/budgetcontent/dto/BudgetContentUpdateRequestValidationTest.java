package io.clueterra.api.admin.budgetcontent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class BudgetContentUpdateRequestValidationTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidEditorialContent() {
        assertThat(validator.validate(validRequest(List.of(validCost("https://example.com/cost")))))
                .isEmpty();
    }

    @Test
    void acceptsCuratedSourcesAndWorldBankRates() {
        BudgetContentCostClaimInput cost = validCost(
                "https://clueterra.com/data-sources", "WORLD_BANK", "CLUETERRA_RESEARCH");
        BudgetContentMetricInput metric = new BudgetContentMetricInput(
                "LENGTH", new BigDecimal("9220"), "M", new BigDecimal("9.22"), "KM",
                "FTA", "Federal Transit Administration", "https://www.transit.dot.gov",
                "https://www.transit.dot.gov/capital-cost-database", "FTA_LENGTH",
                "Published route length", true);
        BudgetContentMilestoneInput milestone = new BudgetContentMilestoneInput(
                "PLANNED_COMPLETION", null, 2028, "YEAR", "GAO",
                "U.S. Government Accountability Office", "https://www.gao.gov",
                "https://www.gao.gov/products/gao-25-107591", "GAO_PLANNED_COMPLETION",
                "Original schedule baseline", true);

        assertThat(validator.validate(cost)).isEmpty();
        assertThat(validator.validate(metric)).isEmpty();
        assertThat(validator.validate(milestone)).isEmpty();
    }

    @Test
    void acceptsSourceStatedExchangeRateBasis() {
        BudgetContentCostClaimInput cost = validCost(
                "https://example.com/official-cost",
                "NONE",
                "CLUETERRA_RESEARCH",
                "SOURCE_STATED");

        assertThat(validator.validate(cost)).isEmpty();
    }

    @Test
    void rejectsMoreThanFiftyCostClaims() {
        BudgetContentUpdateRequest request = validRequest(
                Collections.nCopies(51, validCost("https://example.com/cost")));

        Set<ConstraintViolation<BudgetContentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("costClaims");
    }

    @Test
    void rejectsOversizedNestedSourceUrl() {
        String oversizedUrl = "https://example.com/" + "a".repeat(2000);
        BudgetContentUpdateRequest request = validRequest(List.of(validCost(oversizedUrl)));

        Set<ConstraintViolation<BudgetContentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.endsWith("sourceUrl"));
    }

    private BudgetContentUpdateRequest validRequest(List<BudgetContentCostClaimInput> costs) {
        return new BudgetContentUpdateRequest(
                "Channel Tunnel",
                "A sufficiently descriptive editorial summary for the project.",
                "GB",
                "United Kingdom",
                "EUROPE",
                1994,
                "MEDIUM",
                "File:Channel Tunnel.jpg",
                null,
                costs,
                List.of(),
                List.of());
    }

    private BudgetContentCostClaimInput validCost(String sourceUrl) {
        return validCost(sourceUrl, "ECB", "WIKIPEDIA");
    }

    private BudgetContentCostClaimInput validCost(
            String sourceUrl, String exchangeRateProvider, String sourceCode) {
        return validCost(sourceUrl, exchangeRateProvider, sourceCode, "ANNUAL_AVERAGE");
    }

    private BudgetContentCostClaimInput validCost(
            String sourceUrl,
            String exchangeRateProvider,
            String sourceCode,
            String exchangeRateBasis) {
        return new BudgetContentCostClaimInput(
                "FINAL_COST",
                new BigDecimal("4650000000"),
                new BigDecimal("4650000000"),
                "GBP",
                new BigDecimal("5967659138"),
                new BigDecimal("5967659138"),
                1994,
                new BigDecimal("0.7792"),
                LocalDate.of(1994, 1, 1),
                exchangeRateBasis,
                exchangeRateProvider,
                sourceCode,
                "Wikipedia article lead",
                sourceUrl,
                "https://example.com/reference",
                "Converted from GBP to USD using the annual average rate.",
                "Whole project",
                "WIKIPEDIA_LEAD_COST",
                "Published cost statement.",
                "SOURCE_BACKED",
                true,
                true);
    }
}
