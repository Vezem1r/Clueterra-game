package io.clueterra.api.auth.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope
) {
}
