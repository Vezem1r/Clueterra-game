package io.clueterra.api.budget.model;

public record BudgetGuestClaimRow(
        String status, Long guestUserId, Long targetUserId, Long sessionId, String sessionStatus) {
}
