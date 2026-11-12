package io.clueterra.api.admin.hints.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record CreateHintRequest(
        @NotNull Long countryId,
        @NotBlank @Size(max = 30) String category,
        @NotBlank @Size(max = 30) String hintType,
        @NotBlank @Size(max = 10000) String hintText,
        @NotNull @Min(1) @Max(100) Integer difficulty,
        @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal qualityScore,
        Integer sourceYear) {
}
