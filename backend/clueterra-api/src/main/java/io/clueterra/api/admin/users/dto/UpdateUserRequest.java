package io.clueterra.api.admin.users.dto;

public record UpdateUserRequest(
        String role,
        Boolean enabled,
        Boolean reportsMuted,
        Boolean profileLocked) {
}
