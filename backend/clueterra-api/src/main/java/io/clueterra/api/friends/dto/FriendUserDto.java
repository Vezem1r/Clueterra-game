package io.clueterra.api.friends.dto;

public record FriendUserDto(
        String userId,
        String username,
        String displayName,
        String avatarUrl,
        long totalXp,
        int level) {
}
