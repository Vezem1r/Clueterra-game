package io.clueterra.api.leaderboard.dto;

import java.util.List;

public record LeaderboardPageResponse(
        List<LeaderboardEntryDto> entries,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
