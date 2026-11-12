package io.clueterra.api.auth.dto.guestClaim;

public enum GuestProgressClaimStatus {
    CLAIMED_IN_PROGRESS,
    CLAIMED_COMPLETED,
    NO_GUEST,
    NO_TODAY_PROGRESS,
    TARGET_ALREADY_PLAYED,
    TARGET_NOT_FOUND
}
