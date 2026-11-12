package io.clueterra.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        @JsonIgnore
        String accessToken,
        @JsonIgnore
        String refreshToken,
        String email,
        String username,
        String role,
        long expiresIn,
        boolean totpRequired,
        boolean legalAcceptanceRequired,
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId) {
}
