package io.clueterra.api.admin.analytics.dto;

import java.time.LocalDate;

public record DailySessionDto(
        LocalDate date,
        int totalSessions,
        int completedSessions,
        int uniqueUsers,
        int totalGuesses,
        int correctGuesses) {
}
