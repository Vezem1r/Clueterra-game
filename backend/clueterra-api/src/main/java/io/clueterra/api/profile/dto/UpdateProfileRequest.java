package io.clueterra.api.profile.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "Display name must be at most 50 characters") String displayName,
        @Size(max = 255, message = "Avatar URL must be at most 255 characters") String avatarUrl,
        Boolean socialDiscoverable) {
}
