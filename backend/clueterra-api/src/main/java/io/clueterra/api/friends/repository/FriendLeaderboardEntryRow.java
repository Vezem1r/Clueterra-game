package io.clueterra.api.friends.repository;

public record FriendLeaderboardEntryRow(
        int rank,
        FriendUserRow user,
        long scoreXp,
        long totalXp,
        int level,
        boolean currentUser) {
}
