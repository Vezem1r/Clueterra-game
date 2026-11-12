package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetGeneratedQuestionPage(
        List<BudgetGeneratedQuestionDto> content,
        long totalElements,
        int page,
        int size,
        int totalPages) {
}
