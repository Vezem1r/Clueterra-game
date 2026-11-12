package io.clueterra.api.budget.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.budget.model.BudgetGameSessionStatus;

public record BudgetGameSessionDto(
        @JsonSerialize(using = ToStringSerializer.class)
        long id,
        LocalDate challengeDate,
        BudgetGameSessionStatus status,
        boolean guest,
        int currentPosition,
        int questionCount,
        int totalXpEarned,
        int streakBonusXp,
        int maximumXp,
        int fullXpThresholdPercent,
        long xpBefore,
        int levelBefore,
        Instant startedAt,
        Instant completedAt,
        List<BudgetAnsweredQuestionDto> answeredQuestions,
        BudgetChallengeQuestionDto currentQuestion) {
}
