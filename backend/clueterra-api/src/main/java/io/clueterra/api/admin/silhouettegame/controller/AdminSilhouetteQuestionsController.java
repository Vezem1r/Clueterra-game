package io.clueterra.api.admin.silhouettegame.controller;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoChangeResponse;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoReplaceRequest;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewPage;
import io.clueterra.api.admin.silhouettegame.service.SilhouetteGeneratedContentService;
import io.clueterra.api.admin.silhouettegame.service.SilhouettePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/silhouettes")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Silhouette game", description = "Generated Country silhouettes content and play statistics")
public class AdminSilhouetteQuestionsController {

    private static final String ISO2 = "^[A-Za-z]{2}$";

    private final SilhouetteGeneratedContentService generatedContentService;
    private final SilhouettePhotoService photoService;

    @GetMapping("/questions")
    @Operation(summary = "Inspect generated silhouette questions, their clues, verified facts and how players do on them")
    public ResponseEntity<SilhouetteQuestionPreviewPage> findQuestions(
            @RequestParam(defaultValue = "") @Pattern(regexp = "^([A-Za-z]{2})?$") String countryCode,
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|ACTIVE|INACTIVE)$") String status,
            @RequestParam(required = false) @Min(1) @Max(5) Integer tier,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|WITH_PHOTO|MISSING|MANUAL)$") String photo,
            @RequestParam(defaultValue = "COUNTRY")
            @Pattern(regexp = "^(COUNTRY|EASIEST|HARDEST|MOST_PLAYED|MOST_SKIPPED|NEWEST)$") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(generatedContentService.findQuestions(
                countryCode.toUpperCase(Locale.ROOT), search.strip(), status, tier, photo, sort, page, size));
    }

    @PutMapping("/questions/{iso2}/photo")
    @Operation(summary = "Replace a country landmark photo with a validated Wikimedia Commons file that the sync keeps")
    public ResponseEntity<SilhouettePhotoChangeResponse> replacePhoto(
            @PathVariable @Pattern(regexp = ISO2) String iso2,
            @Valid @RequestBody SilhouettePhotoReplaceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(photoService.replace(iso2.toUpperCase(Locale.ROOT), request, authentication.getName()));
    }

    @DeleteMapping("/questions/{iso2}/photo")
    @Operation(summary = "Leave a country without a landmark photo until it is returned to the automatic choice")
    public ResponseEntity<SilhouettePhotoChangeResponse> removePhoto(
            @PathVariable @Pattern(regexp = ISO2) String iso2,
            Authentication authentication) {
        return ResponseEntity.ok(photoService.remove(iso2.toUpperCase(Locale.ROOT), authentication.getName()));
    }

    @PostMapping("/questions/{iso2}/photo/automatic")
    @Operation(summary = "Return a country landmark photo to the automatic choice and start the photo sync")
    public ResponseEntity<SilhouettePhotoChangeResponse> automaticPhoto(@PathVariable @Pattern(regexp = ISO2) String iso2) {
        return ResponseEntity.ok(photoService.automatic(iso2.toUpperCase(Locale.ROOT)));
    }
}
