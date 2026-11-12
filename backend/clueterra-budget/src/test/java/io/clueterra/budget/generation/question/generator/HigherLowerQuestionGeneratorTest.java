package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;

class HigherLowerQuestionGeneratorTest {

    private HigherLowerQuestionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HigherLowerQuestionGenerator(
                new BudgetQuestionPayloadFactory(new ObjectMapper()));
    }

    @ParameterizedTest(name = "expensive question id={0} -> answer={1}")
    @CsvSource({"2, COMPARISON", "3, PRIMARY"})
    @DisplayName("should point the answer at the more expensive project regardless of display order")
    void shouldPointAnswerAtMoreExpensiveProject(long expensiveQuestionId, String expectedAnswer) {
        var cheaper = context(1, 10, "100", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var expensive = context(
                expensiveQuestionId,
                20,
                "150",
                BudgetDifficulty.HARD,
                MegaprojectCategory.BRIDGE);

        var generated = generator.generate(List.of(cheaper, expensive)).getFirst();

        assertThat(generated.answerChoice()).isEqualTo(expectedAnswer);
        assertThat(generated.difficulty()).isEqualTo(BudgetDifficulty.HARD);
        long answeredQuestionId = "PRIMARY".equals(expectedAnswer)
                ? generated.sourceQuestionId()
                : generated.comparisonQuestionId();
        assertThat(answeredQuestionId).isEqualTo(expensiveQuestionId);
    }

    @ParameterizedTest(name = "cost ratio={0} -> generated={1}")
    @CsvSource({"1.19, false", "1.20, true", "2.00, true"})
    @DisplayName("should require at least a twenty-percent cost gap")
    void shouldRequireMinimumCostGap(String costRatio, boolean expectedToGenerate) {
        var cheaper = context(1, 10, "100", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var expensive = context(
                2,
                20,
                new BigDecimal("100").multiply(new BigDecimal(costRatio)).toPlainString(),
                BudgetDifficulty.EASY,
                MegaprojectCategory.BRIDGE);

        assertThat(generator.generate(List.of(cheaper, expensive)))
                .hasSize(expectedToGenerate ? 1 : 0);
    }

    @Test
    @DisplayName("should not compare projects from different categories")
    void shouldNotCompareProjectsFromDifferentCategories() {
        var bridge = context(1, 10, "100", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var airport = context(2, 20, "500", BudgetDifficulty.EASY, MegaprojectCategory.AIRPORT);

        assertThat(generator.generate(List.of(bridge, airport))).isEmpty();
    }

    @Test
    @DisplayName("should rebuild pairs across the combined old and newly approved project pool")
    void shouldRebuildPairsAcrossOldAndNewProjects() {
        var oldCheap = context(1, 10, "100", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var oldExpensive = context(2, 20, "200", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var newCheap = context(3, 30, "130", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        var newExpensive = context(4, 40, "300", BudgetDifficulty.EASY, MegaprojectCategory.BRIDGE);
        Set<Long> oldQuestionIds = Set.of(1L, 2L);

        var oldOnly = generator.generate(List.of(oldCheap, oldExpensive));
        var newOnly = generator.generate(List.of(newCheap, newExpensive));
        var combined = generator.generate(List.of(
                oldCheap,
                oldExpensive,
                newCheap,
                newExpensive));

        assertThat(oldOnly).hasSize(1);
        assertThat(newOnly).hasSize(1);
        assertThat(combined).hasSizeGreaterThan(oldOnly.size() + newOnly.size());
        assertThat(combined).allSatisfy(pair -> {
            boolean sourceIsOld = oldQuestionIds.contains(pair.sourceQuestionId());
            boolean comparisonIsOld = oldQuestionIds.contains(pair.comparisonQuestionId());
            assertThat(sourceIsOld).isNotEqualTo(comparisonIsOld);
        });
    }

    private BudgetQuestionGenerationContext context(
            long questionId,
            long projectId,
            String cost,
            BudgetDifficulty difficulty,
            MegaprojectCategory category) {
        return new BudgetQuestionGenerationContext(
                source(questionId, projectId, category, new BigDecimal(cost), difficulty),
                List.of(),
                List.of(),
                List.of());
    }
}
