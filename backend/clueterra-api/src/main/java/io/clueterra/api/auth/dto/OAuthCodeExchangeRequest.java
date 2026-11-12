package io.clueterra.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeExchangeRequest(@NotBlank String code) {
}
