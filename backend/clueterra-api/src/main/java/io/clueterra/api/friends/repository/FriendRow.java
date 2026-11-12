package io.clueterra.api.friends.repository;

import java.time.Instant;

public record FriendRow(
        Long friendshipId,
        FriendUserRow user,
        Instant friendsSince) {
}
