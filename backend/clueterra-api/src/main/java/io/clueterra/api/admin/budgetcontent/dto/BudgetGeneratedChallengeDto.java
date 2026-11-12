package io.clueterra.api.admin.budgetcontent.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BudgetGeneratedChallengeDto(
        long id,
        LocalDate challengeDate,
        String generationTrigger,
        String status,
        Instant publishedAt,
        boolean deletable,
        List<BudgetGeneratedChallengeQuestionDto> questions) {
}
