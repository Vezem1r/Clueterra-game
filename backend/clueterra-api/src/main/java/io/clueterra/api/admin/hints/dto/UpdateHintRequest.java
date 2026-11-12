package io.clueterra.api.admin.hints.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateHintRequest(
        @Size(min = 1, max = 10000, message = "Hint text must be between 1 and 10000 characters")
        String hintText,
        @Min(value = 1, message = "Difficulty must be at least 1")
        @Max(value = 100, message = "Difficulty cannot exceed 100")
        Integer difficulty,
        @DecimalMin(value = "0.00", message = "Quality score must be at least 0.00")
        @DecimalMax(value = "1.00", message = "Quality score cannot exceed 1.00")
        BigDecimal qualityScore,
        Boolean isActive) {
}
