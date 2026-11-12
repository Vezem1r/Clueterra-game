package io.clueterra.budget.generation.question.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetQuestionPromptPayload(
        BudgetPromptProject project,
        BudgetPromptProject primaryProject,
        BudgetPromptProject comparisonProject,
        BudgetPromptMetric metric) {

    public BudgetQuestionPromptPayload {
        boolean unary = project != null && primaryProject == null && comparisonProject == null;
        boolean comparison = project == null && primaryProject != null && comparisonProject != null;
        if (!unary && !comparison) {
            throw new IllegalArgumentException("Prompt must describe one project or one project pair");
        }
        if (comparison && metric != null) {
            throw new IllegalArgumentException("Comparison prompt cannot contain a metric");
        }
    }
}
