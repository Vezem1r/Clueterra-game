package io.clueterra.api.auth.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserResponse(
        Long id,
        String login,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
