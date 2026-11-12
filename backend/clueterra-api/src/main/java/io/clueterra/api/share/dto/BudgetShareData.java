package io.clueterra.api.share.dto;

import java.time.LocalDate;
import java.util.List;

public record BudgetShareData(
        LocalDate date,
        int totalXpEarned,
        int maximumXp,
        double averageAccuracy,
        List<BudgetShareBuilding> buildings) {
}
