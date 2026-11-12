package io.clueterra.api.budget.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.clueterra.api.budget.model.BudgetQuestionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetQuestionPromptDto(
        BudgetPromptProjectDto project,
        BudgetPromptProjectDto primaryProject,
        BudgetPromptProjectDto comparisonProject,
        BudgetPromptMetricDto metric) {

    public BudgetQuestionPromptDto {
        boolean unary = project != null && primaryProject == null && comparisonProject == null;
        boolean comparison = project == null && primaryProject != null && comparisonProject != null;
        if (!unary && !comparison) {
            throw new IllegalArgumentException("Prompt must describe one project or one project pair");
        }
        if (comparison && metric != null) {
            throw new IllegalArgumentException("Comparison prompt cannot contain a metric");
        }
    }

    public BudgetQuestionPromptDto validateFor(BudgetQuestionType type) {
        Objects.requireNonNull(type, "Budget question type is required");
        boolean comparison = primaryProject != null;
        boolean valid = switch (type) {
            case HIGHER_LOWER -> comparison;
            case COST_PER_UNIT -> !comparison && metric != null;
            default -> !comparison && metric == null;
        };
        if (!valid) {
            throw new IllegalArgumentException("Prompt shape does not match Budget question type " + type);
        }
        return this;
    }
}
