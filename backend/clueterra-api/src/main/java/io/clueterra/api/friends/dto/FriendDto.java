package io.clueterra.api.friends.dto;

import java.time.Instant;

public record FriendDto(
        String friendshipId,
        FriendUserDto user,
        Instant friendsSince) {
}
