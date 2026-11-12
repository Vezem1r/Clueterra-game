package io.clueterra.api.legal.dto;

public record LegalCurrentResponse(
        String termsVersion,
        String privacyVersion,
        String effectiveDate) {
}
