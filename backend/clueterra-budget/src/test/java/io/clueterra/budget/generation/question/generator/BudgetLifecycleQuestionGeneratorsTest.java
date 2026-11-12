package io.clueterra.budget.generation.question.generator;

import static io.clueterra.budget.generation.support.BudgetGenerationFixtures.cost;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationContext;
import io.clueterra.budget.generation.question.model.BudgetSourceQuestion;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectLifecycleStatus;

class BudgetLifecycleQuestionGeneratorsTest {

    private BudgetQuestionPayloadFactory payloadFactory;

    @BeforeEach
    void setUp() {
        payloadFactory = new BudgetQuestionPayloadFactory(new ObjectMapper());
    }

    @Test
    @DisplayName("should use the current estimate for an unfinished project")
    void shouldGenerateCurrentEstimateQuestion() {
        var source = source(ProjectCostKind.CURRENT_ESTIMATE, ProjectLifecycleStatus.UNDER_CONSTRUCTION);
        var context = new BudgetQuestionGenerationContext(source, List.of(), List.of(), List.of());

        var generated = new CurrentEstimateQuestionGenerator(payloadFactory).generate(List.of(context));

        assertThat(generated).singleElement().satisfies(question -> {
            assertThat(question.answerValue()).isEqualByComparingTo("750000000");
            assertThat(question.answerUnit()).isEqualTo("USD");
        });
    }

    @Test
    @DisplayName("should not call a cancelled project's historical estimate current")
    void shouldNotGenerateCurrentEstimateForCancelledProject() {
        var source = source(ProjectCostKind.CURRENT_ESTIMATE, ProjectLifecycleStatus.CANCELLED);
        var context = new BudgetQuestionGenerationContext(source, List.of(), List.of(), List.of());

        var generated = new CurrentEstimateQuestionGenerator(payloadFactory).generate(List.of(context));

        assertThat(generated).isEmpty();
    }

    @Test
    @DisplayName("should generate spent-so-far only for unfinished projects with expenditure data")
    void shouldGenerateSpentSoFarForUnfinishedProject() {
        var unfinished = source(ProjectCostKind.CURRENT_ESTIMATE, ProjectLifecycleStatus.ON_HOLD);
        var completed = source(ProjectCostKind.REPORTED_COST, ProjectLifecycleStatus.COMPLETED);
        var expenditure = cost(
                unfinished.projectId(),
                ProjectCostKind.EXPENDITURE_TO_DATE,
                new BigDecimal("200000000"),
                new BigDecimal("240000000"));
        var unfinishedContext = new BudgetQuestionGenerationContext(
                unfinished, List.of(expenditure), List.of(), List.of());
        var completedContext = new BudgetQuestionGenerationContext(
                completed, List.of(expenditure), List.of(), List.of());

        var generated = new SpentSoFarQuestionGenerator(payloadFactory)
                .generate(List.of(unfinishedContext, completedContext));

        assertThat(generated).singleElement().satisfies(question -> {
            assertThat(question.sourceQuestionId()).isEqualTo(unfinished.questionId());
            assertThat(question.answerValue()).isEqualByComparingTo("220000000");
            assertThat(question.answerUnit()).isEqualTo("USD");
        });
    }

    @Test
    @DisplayName("should not expose unfinished projects through the completed-cost question")
    void shouldExcludeUnfinishedProjectFromHowMuch() {
        var unfinished = source(ProjectCostKind.REPORTED_COST, ProjectLifecycleStatus.PLANNED);
        var context = new BudgetQuestionGenerationContext(unfinished, List.of(), List.of(), List.of());

        var generated = new HowMuchQuestionGenerator(payloadFactory).generate(List.of(context));

        assertThat(generated).isEmpty();
    }

    private BudgetSourceQuestion source(
            ProjectCostKind answerCostKind,
            ProjectLifecycleStatus lifecycleStatus) {
        return new BudgetSourceQuestion(
                lifecycleStatus == ProjectLifecycleStatus.COMPLETED ? 2 : 1,
                lifecycleStatus == ProjectLifecycleStatus.COMPLETED ? 20 : 10,
                lifecycleStatus == ProjectLifecycleStatus.COMPLETED ? "Q20" : "Q10",
                "Test project",
                "Source-backed project description",
                "Wikipedia contributors",
                "https://en.wikipedia.org/w/index.php?oldid=1",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "US",
                "United States",
                "NORTH_AMERICA",
                MegaprojectCategory.ROAD_HIGHWAY,
                lifecycleStatus == ProjectLifecycleStatus.COMPLETED ? 2020 : null,
                answerCostKind,
                lifecycleStatus,
                lifecycleStatus.isUnfinished() ? 2030 : null,
                new BigDecimal("750000000"),
                "Official source",
                "https://example.com/source",
                "https://example.com/reference",
                BudgetDifficulty.MEDIUM);
    }
}
