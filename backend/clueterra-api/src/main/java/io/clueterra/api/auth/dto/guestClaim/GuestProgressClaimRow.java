package io.clueterra.api.auth.dto.guestClaim;

public record GuestProgressClaimRow(
        String claimStatus,
        Long guestUserId,
        Long targetUserId,
        Long sessionId,
        String sessionStatus) {
}
