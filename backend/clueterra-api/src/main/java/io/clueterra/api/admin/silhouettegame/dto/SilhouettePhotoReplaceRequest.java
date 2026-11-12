package io.clueterra.api.admin.silhouettegame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SilhouettePhotoReplaceRequest(
        @NotBlank @Size(max = 500) String fileTitle,
        @Pattern(regexp = "^Q[1-9][0-9]*$") String landmarkQid) {
}
