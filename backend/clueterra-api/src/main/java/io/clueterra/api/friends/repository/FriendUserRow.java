package io.clueterra.api.friends.repository;

public record FriendUserRow(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        long totalXp,
        int level) {
}
