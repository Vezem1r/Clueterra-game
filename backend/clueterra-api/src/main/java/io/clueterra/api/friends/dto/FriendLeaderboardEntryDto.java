package io.clueterra.api.friends.dto;

public record FriendLeaderboardEntryDto(
        int rank,
        FriendUserDto user,
        long scoreXp,
        long totalXp,
        int level,
        boolean currentUser) {
}
