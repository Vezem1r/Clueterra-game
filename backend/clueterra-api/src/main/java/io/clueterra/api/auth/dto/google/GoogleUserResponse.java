package io.clueterra.api.auth.dto.google;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleUserResponse(
        String id,
        String email,
        String name,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        String picture,
        @JsonProperty("verified_email") boolean verifiedEmail
) {
}
