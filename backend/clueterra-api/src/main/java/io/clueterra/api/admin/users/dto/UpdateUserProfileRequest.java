package io.clueterra.api.admin.users.dto;

public record UpdateUserProfileRequest(
        String username,
        String displayName,
        Integer totalXp,
        Integer challengesPlayed,
        Integer challengesWon,
        Integer currentStreak,
        Integer bestStreak,
        Integer streakFreezes) {
}
