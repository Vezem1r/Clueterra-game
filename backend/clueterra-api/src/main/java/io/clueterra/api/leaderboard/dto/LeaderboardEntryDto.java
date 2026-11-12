package io.clueterra.api.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LeaderboardEntryDto(
        @JsonIgnore Long userId,
        String username,
        String displayName,
        String avatarUrl,
        long totalXp,
        int level,
        int challengesWon,
        int rank) {
}
