package io.clueterra.api.friends.dto;

import java.util.List;

public record FriendRequestsDto(
        List<FriendRequestDto> incoming,
        List<FriendRequestDto> outgoing) {
}
