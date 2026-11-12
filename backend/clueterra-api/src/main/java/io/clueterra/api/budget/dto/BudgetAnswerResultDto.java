package io.clueterra.api.budget.dto;

public record BudgetAnswerResultDto(
        boolean replayed, BudgetAnsweredQuestionDto answer, BudgetGameSessionDto session) {
}
