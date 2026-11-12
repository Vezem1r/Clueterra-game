package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.cost;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.milestone;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectMilestoneType;

class BudgetDerivedQuestionGeneratorsTest {

    private BudgetQuestionPayloadFactory payloadFactory;

    @BeforeEach
    void setUp() {
        payloadFactory = new BudgetQuestionPayloadFactory(new ObjectMapper());
    }

    @ParameterizedTest(name = "final cost={0} -> generated={1}")
    @CsvSource({"109, false", "110, true", "150, true", "90, false"})
    @DisplayName("should generate budget-overrun questions only for overruns of at least ten percent")
    void shouldGenerateBudgetOverrunOnlyAtPlayableThreshold(
            String finalCost, boolean expectedToGenerate) {
        var source = source(
                1,
                10,
                MegaprojectCategory.BRIDGE,
                new BigDecimal(finalCost),
                BudgetDifficulty.MEDIUM);
        var original = cost(
                source.projectId(),
                ProjectCostKind.ORIGINAL_BUDGET,
                new BigDecimal("100"),
                new BigDecimal("100"));
        var context = new BudgetQuestionGenerationContext(
                source, List.of(original), List.of(), List.of());

        var generated = new BudgetOverrunQuestionGenerator(payloadFactory).generate(List.of(context));

        assertThat(generated).hasSize(expectedToGenerate ? 1 : 0);
        if (expectedToGenerate) {
            assertThat(generated.getFirst().answerUnit()).isEqualTo("PERCENT");
            assertThat(generated.getFirst().difficulty()).isEqualTo(BudgetDifficulty.HARD);
        }
    }

    @Test
    @DisplayName("should generate original-budget answer from a ranged estimate midpoint")
    void shouldGenerateOriginalBudgetFromRangedEstimateMidpoint() {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, new BigDecimal("200"));
        var original = cost(
                source.projectId(),
                ProjectCostKind.ORIGINAL_BUDGET,
                new BigDecimal("80"),
                new BigDecimal("120"));
        var withOriginal = new BudgetQuestionGenerationContext(
                source, List.of(original), List.of(), List.of());
        var withoutOriginal = new BudgetQuestionGenerationContext(
                source(2, 20, MegaprojectCategory.BRIDGE, new BigDecimal("300")),
                List.of(),
                List.of(),
                List.of());

        var generated = new OriginalBudgetQuestionGenerator(payloadFactory)
                .generate(List.of(withOriginal, withoutOriginal));

        assertThat(generated).singleElement().satisfies(question -> {
            assertThat(question.sourceQuestionId()).isEqualTo(source.questionId());
            assertThat(question.answerValue()).isEqualByComparingTo("100");
            assertThat(question.answerUnit()).isEqualTo("USD");
        });
    }

    @Test
    @DisplayName("should generate how-late answer only when completion happened after the plan")
    void shouldGenerateHowLateOnlyForDelayedProjects() {
        var delayedSource = source(1, 10, MegaprojectCategory.AIRPORT, new BigDecimal("200"));
        var onTimeSource = source(2, 20, MegaprojectCategory.AIRPORT, new BigDecimal("300"));
        var delayed = new BudgetQuestionGenerationContext(
                delayedSource,
                List.of(),
                List.of(),
                List.of(
                        milestone(delayedSource.projectId(), ProjectMilestoneType.PLANNED_COMPLETION, 2018),
                        milestone(delayedSource.projectId(), ProjectMilestoneType.OPENING, 2020)));
        var onTime = new BudgetQuestionGenerationContext(
                onTimeSource,
                List.of(),
                List.of(),
                List.of(
                        milestone(onTimeSource.projectId(), ProjectMilestoneType.PLANNED_COMPLETION, 2020),
                        milestone(onTimeSource.projectId(), ProjectMilestoneType.ACTUAL_COMPLETION, 2020)));

        var generated = new HowLateQuestionGenerator(payloadFactory).generate(List.of(delayed, onTime));

        assertThat(generated).singleElement().satisfies(question -> {
            assertThat(question.sourceQuestionId()).isEqualTo(delayedSource.questionId());
            assertThat(question.answerValue()).isEqualByComparingTo("24");
            assertThat(question.answerUnit()).isEqualTo("MONTHS");
        });
    }
}
