package io.clueterra.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.clueterra.api.common.validation.MaxUtf8Bytes;

public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
        String username,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "Password must not exceed 72 bytes")
        String password,
        @NotBlank(message = "Terms version is required") String termsVersion,
        @NotBlank(message = "Privacy version is required") String privacyVersion) {}
