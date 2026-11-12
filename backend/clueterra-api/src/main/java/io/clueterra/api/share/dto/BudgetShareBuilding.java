package io.clueterra.api.share.dto;

public record BudgetShareBuilding(
        int position,
        int styleId,
        double accuracyPercent,
        int xpEarned) {
}
