package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetProjectDifficultyCalculatorTest {

    private final DynamicDifficultyCalculator dynamicDifficultyCalculator = mock(DynamicDifficultyCalculator.class);
    private final BudgetProjectDifficultyCalculator calculator =
            new BudgetProjectDifficultyCalculator(dynamicDifficultyCalculator);

    @BeforeEach
    void setUp() {
        when(dynamicDifficultyCalculator.computeDifficulty(anyString(), eq(1), eq(43))).thenReturn(46);
    }

    @Test
    void recognizableAndObscureProjectsInTheSameCountryHaveDifferentDifficulty() {
        BudgetProjectHintFacts detailed = project(2015, BigDecimal.valueOf(2_400));

        int iconicUsProject = calculator.calculate("BUDGET_PROJECT_Q1:PROJECT",
                new BigDecimal("0.20"), "EASY", detailed);
        int obscureUsProject = calculator.calculate("BUDGET_PROJECT_Q2:PROJECT",
                new BigDecimal("0.20"), "HARD", detailed);

        assertThat(iconicUsProject).isEqualTo(18);
        assertThat(obscureUsProject).isEqualTo(44);
    }

    @Test
    void countryAndVisibleAnchorsBothAffectDifficulty() {
        BudgetProjectHintFacts detailed = project(2015, BigDecimal.valueOf(2_400));
        BudgetProjectHintFacts sparse = project(null, null);

        int detailedUs = calculator.calculate("BUDGET_PROJECT_Q1:PROJECT",
                new BigDecimal("0.20"), "HARD", detailed);
        int sparseUs = calculator.calculate("BUDGET_PROJECT_Q1:PROJECT",
                new BigDecimal("0.20"), "HARD", sparse);
        int sparseHarderCountry = calculator.calculate("BUDGET_PROJECT_Q1:PROJECT",
                new BigDecimal("1.40"), "HARD", sparse);

        assertThat(detailedUs).isLessThan(sparseUs);
        assertThat(sparseUs).isLessThan(sparseHarderCountry);
    }

    private BudgetProjectHintFacts project(Integer year, BigDecimal metricValue) {
        return new BudgetProjectHintFacts("Example Bridge", "BRIDGE", "COMPLETED", year,
                BigDecimal.valueOf(2_000_000), "FINAL_COST",
                metricValue == null ? null : "LENGTH", metricValue);
    }
}
