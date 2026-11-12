package io.clueterra.api.auth.dto.google;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("id_token") String idToken
) {
}
