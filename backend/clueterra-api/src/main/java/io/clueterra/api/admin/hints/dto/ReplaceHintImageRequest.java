package io.clueterra.api.admin.hints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplaceHintImageRequest(
        @NotBlank(message = "Wikimedia Commons file name is required")
        @Size(max = 500, message = "Wikimedia Commons file name is too long")
        String fileTitle) {
}
