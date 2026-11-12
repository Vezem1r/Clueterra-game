package io.clueterra.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TotpLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank @Size(min = 6, max = 6) String totpCode) {
}
