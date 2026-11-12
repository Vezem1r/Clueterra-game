package io.clueterra.budget.generation.question.model;

public record BudgetQuestionDelay(
        BudgetQuestionMilestoneFact planned,
        BudgetQuestionMilestoneFact actual,
        long months) {
}
