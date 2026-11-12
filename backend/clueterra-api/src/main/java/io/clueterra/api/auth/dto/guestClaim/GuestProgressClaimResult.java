package io.clueterra.api.auth.dto.guestClaim;

public record GuestProgressClaimResult(
        GuestProgressClaimStatus status,
        Long guestUserId,
        Long targetUserId,
        Long sessionId,
        String sessionStatus) {
}
