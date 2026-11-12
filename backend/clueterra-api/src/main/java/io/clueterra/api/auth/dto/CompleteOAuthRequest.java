package io.clueterra.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteOAuthRequest(
        @NotBlank String pendingToken,
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores and hyphens")
        String username,
        @NotBlank(message = "Terms version is required") String termsVersion,
        @NotBlank(message = "Privacy version is required") String privacyVersion) {
}
