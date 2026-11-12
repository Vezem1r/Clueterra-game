package io.clueterra.api.legal.dto;

import jakarta.validation.constraints.NotBlank;

public record LegalAcceptanceRequest(
        @NotBlank(message = "Terms version is required") String termsVersion,
        @NotBlank(message = "Privacy version is required") String privacyVersion) {
}
