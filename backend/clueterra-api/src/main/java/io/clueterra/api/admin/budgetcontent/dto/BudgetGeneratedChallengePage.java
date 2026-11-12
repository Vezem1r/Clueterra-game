package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetGeneratedChallengePage(
        List<BudgetGeneratedChallengeDto> content,
        long totalElements,
        int page,
        int size,
        int totalPages) {
}
