package io.clueterra.api.friends.repository;

import java.time.Instant;

public record FriendRequestRow(
        Long requestId,
        FriendUserRow user,
        String direction,
        Instant createdAt) {
}
