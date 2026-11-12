package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.metric;
import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectMetricType;

class CostPerUnitQuestionGeneratorTest {

    private CostPerUnitQuestionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CostPerUnitQuestionGenerator(
                new BudgetQuestionPayloadFactory(new ObjectMapper()));
    }

    @ParameterizedTest(name = "{0}: {1} -> {4}")
    @MethodSource("supportedCategoryMetrics")
    @DisplayName("should generate category-specific normalized cost-per-unit questions")
    void shouldGenerateCategorySpecificCostPerUnitQuestions(
            MegaprojectCategory category,
            ProjectMetricType metricType,
            String metricValue,
            String metricUnit,
            String expectedAnswerUnit,
            String expectedAnswer,
            String expectedDisplayValue) {
        var source = source(1, 10, category, new BigDecimal("1000000000"));
        var metric = metric(source.projectId(), metricType, new BigDecimal(metricValue), metricUnit);
        var context = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(metric), List.of());

        BudgetGeneratedQuestionDraft generated = generator.generate(List.of(context)).getFirst();

        assertThat(generated.answerUnit()).isEqualTo(expectedAnswerUnit);
        assertThat(generated.answerValue()).isEqualByComparingTo(expectedAnswer);
        assertThat(generated.promptPayload().metric().type()).isEqualTo(metricType);
        assertThat(generated.promptPayload().metric().value())
                .isEqualByComparingTo(expectedDisplayValue);
    }

    @ParameterizedTest(name = "{0} prefers {1} over {2}")
    @MethodSource("metricPriorityCases")
    @DisplayName("should use the most meaningful denominator available for each category")
    void shouldUseMostMeaningfulDenominatorAvailable(
            MegaprojectCategory category,
            ProjectMetricType preferredType,
            ProjectMetricType fallbackType,
            String expectedAnswerUnit) {
        var source = source(1, 10, category, new BigDecimal("1000000000"));
        List<BudgetQuestionMetricFact> metrics = List.of(
                metric(source.projectId(), fallbackType, new BigDecimal("300"), fallbackType.normalizedUnit()),
                metric(source.projectId(), preferredType, new BigDecimal("1000"), preferredType.normalizedUnit()));
        var context = new BudgetQuestionGenerationContext(source, List.of(), metrics, List.of());

        BudgetGeneratedQuestionDraft generated = generator.generate(List.of(context)).getFirst();

        assertThat(generated.answerUnit()).isEqualTo(expectedAnswerUnit);
        assertThat(generated.promptPayload().metric().type()).isEqualTo(preferredType);
    }

    @Test
    @DisplayName("should not generate cost-per-unit questions from irrelevant category metrics")
    void shouldNotGenerateFromIrrelevantCategoryMetrics() {
        var source = source(1, 10, MegaprojectCategory.BRIDGE, new BigDecimal("1000000000"));
        var height = metric(
                source.projectId(), ProjectMetricType.HEIGHT, new BigDecimal("300"), "M");
        var context = new BudgetQuestionGenerationContext(
                source, List.of(), List.of(height), List.of());

        assertThat(generator.generate(List.of(context))).isEmpty();
    }

    private static Stream<Arguments> supportedCategoryMetrics() {
        return Stream.of(
                Arguments.of(
                        MegaprojectCategory.BRIDGE, ProjectMetricType.LENGTH, "3900", "M",
                        "USD_PER_KM", "256410256.410256", "3.9"),
                Arguments.of(
                        MegaprojectCategory.METRO_RAIL, ProjectMetricType.LENGTH, "20000", "M",
                        "USD_PER_KM", "50000000", "20"),
                Arguments.of(
                        MegaprojectCategory.STADIUM, ProjectMetricType.CAPACITY, "50000", "SEAT",
                        "USD_PER_SEAT", "20000", "50000"),
                Arguments.of(
                        MegaprojectCategory.SKYSCRAPER, ProjectMetricType.FLOOR_AREA, "100000", "M2",
                        "USD_PER_SQUARE_METER", "10000", "100000"),
                Arguments.of(
                        MegaprojectCategory.AIRPORT, ProjectMetricType.ANNUAL_PASSENGERS,
                        "20000000", "PASSENGERS_PER_YEAR", "USD_PER_ANNUAL_PASSENGER", "50", "20000000"),
                Arguments.of(
                        MegaprojectCategory.TUNNEL, ProjectMetricType.LENGTH, "5000", "M",
                        "USD_PER_KM", "200000000", "5"),
                Arguments.of(
                        MegaprojectCategory.DAM, ProjectMetricType.POWER, "1000", "MW",
                        "USD_PER_MW", "1000000", "1000"),
                Arguments.of(
                        MegaprojectCategory.ENERGY, ProjectMetricType.POWER, "1000", "MW",
                        "USD_PER_MW", "1000000", "1000"),
                Arguments.of(
                        MegaprojectCategory.SPACE, ProjectMetricType.MASS, "200000", "KG",
                        "USD_PER_KG", "5000", "200000"));
    }

    private static Stream<Arguments> metricPriorityCases() {
        return Stream.of(
                Arguments.of(
                        MegaprojectCategory.SKYSCRAPER,
                        ProjectMetricType.FLOOR_AREA,
                        ProjectMetricType.HEIGHT,
                        "USD_PER_SQUARE_METER"),
                Arguments.of(
                        MegaprojectCategory.DAM,
                        ProjectMetricType.POWER,
                        ProjectMetricType.STORAGE_VOLUME,
                        "USD_PER_MW"));
    }
}
