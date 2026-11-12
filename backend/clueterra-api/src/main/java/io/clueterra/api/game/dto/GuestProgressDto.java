package io.clueterra.api.game.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuestProgressDto(
        boolean hasGuest,
        int playCount,
        int budgetPlayCount,
        long totalXp,
        int currentLevel,
        int levelDivisor,
        int currentStreak,
        int bestStreak,
        int badgesEarned,
        Instant expiresAt) {

    public static GuestProgressDto none() {
        return new GuestProgressDto(false, 0, 0, 0, 1, 100, 0, 0, 0, null);
    }
}
