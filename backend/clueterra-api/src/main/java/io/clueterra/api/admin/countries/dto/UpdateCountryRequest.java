package io.clueterra.api.admin.countries.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

public record UpdateCountryRequest(
        @Size(min = 2, max = 100, message = "English name must be between 2 and 100 characters")
        String nameEn,
        @Size(min = 2, max = 100, message = "Native name must be between 2 and 100 characters")
        String nameNative,
        @Size(max = 100) String capital,
        @DecimalMin(value = "0.0", message = "Difficulty modifier must be at least 0.0")
        @DecimalMax(value = "9.99", message = "Difficulty modifier cannot exceed 9.99")
        @Digits(integer = 1, fraction = 2, message = "Must be a decimal with up to 1 integer digit and 2 fractional digits")
        Double difficultyModifier,
        Boolean isActive) {
}
