package io.clueterra.api.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BudgetRevealPayloadSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BudgetRevealPayloadSanitizer sanitizer = new BudgetRevealPayloadSanitizer();

    @Test
    void shouldStripPrivateHintsAndCollapseLegacyExplanation() throws Exception {
        var reveal = objectMapper.readTree("""
                {
                  "hints": [{"type": "ANSWER_RANGE"}],
                  "explanation": {
                    "questionType": "HOW_MUCH",
                    "answerValue": 2300000000,
                    "answerUnit": "USD",
                    "answerBasis": {
                      "sourceAmountLow": 13900000000,
                      "sourceAmountHigh": 13900000000,
                      "sourceCurrency": "CNY",
                      "amountUsdLow": 2300000000,
                      "amountUsdHigh": 2300000000,
                      "costYear": 2013,
                      "unitsPerUsd": 6.1,
                      "exchangeRateProvider": "provider",
                      "evidenceText": "technical evidence",
                      "sourceUrl": "https://example.com"
                    }
                  }
                }
                """);

        var sanitized = sanitizer.sanitize(reveal);

        assertThat(sanitized.has("hints")).isFalse();
        assertThat(sanitized.at("/explanation/conversion/sourceCurrency").asText()).isEqualTo("CNY");
        assertThat(sanitized.at("/explanation/conversion/costYear").asInt()).isEqualTo(2013);
        assertThat(sanitized.at("/explanation/conversion").has("unitsPerUsd")).isFalse();
        assertThat(sanitized.at("/explanation/conversion").has("evidenceText")).isFalse();
    }

    @Test
    void shouldRemoveRedundantUsdExplanationAndTrimNestedReveals() throws Exception {
        var reveal = objectMapper.readTree("""
                {
                  "milestones": [{"type": "ACTUAL_COMPLETION", "year": 2018, "precision": "YEAR"}],
                  "explanation": {
                    "questionType": "HOW_MUCH",
                    "answerBasis": {"sourceCurrency": "USD", "sourceAmountLow": 150000000}
                  },
                  "primary": {"hints": [{"type": "ANSWER_RANGE"}]}
                }
                """);

        var sanitized = sanitizer.sanitize(reveal);

        assertThat(sanitized.has("explanation")).isFalse();
        assertThat(sanitized.at("/primary").has("hints")).isFalse();
    }

    @Test
    void shouldKeepOnlyFormulaFieldsNeededByThePlayer() throws Exception {
        var reveal = objectMapper.readTree("""
                {
                  "explanation": {
                    "questionType": "HOW_LATE",
                    "answerValue": 84,
                    "answerUnit": "MONTHS",
                    "formula": {
                      "operation": "MONTHS_BETWEEN",
                      "planned": {"year": 2011, "sourceName": "Hidden"},
                      "actual": {"year": 2018, "precision": "YEAR"}
                    }
                  }
                }
                """);

        var sanitized = sanitizer.sanitize(reveal);

        assertThat(sanitized.at("/explanation/formula/result").asInt()).isEqualTo(84);
        assertThat(sanitized.at("/explanation/formula/resultUnit").asText()).isEqualTo("MONTHS");
        assertThat(sanitized.at("/explanation/formula/planned").has("sourceName")).isFalse();
        assertThat(sanitized.at("/explanation/formula/actual").has("precision")).isFalse();
        assertThat(sanitized.at("/milestones/0").has("precision")).isFalse();
    }
}
