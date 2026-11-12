package io.clueterra.api.friends.dto;

import java.time.Instant;

public record FriendRequestDto(
        String requestId,
        FriendUserDto user,
        String direction,
        Instant createdAt) {
}
