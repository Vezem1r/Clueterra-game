package io.clueterra.api.admin.rivergame.controller;

import io.clueterra.api.admin.rivergame.dto.RiverQuestionPreviewPage;
import io.clueterra.api.admin.rivergame.service.RiverGeneratedContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rivers")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - River game", description = "Generated Rivers game content")
public class AdminRiverQuestionsController {

    private final RiverGeneratedContentService generatedContentService;

    @GetMapping("/questions")
    @Operation(summary = "Inspect generated river questions, their maps, and source-backed clues")
    public ResponseEntity<RiverQuestionPreviewPage> findQuestions(
            @RequestParam(defaultValue = "") @Pattern(regexp = "^([A-Za-z]{2})?$") String countryCode,
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|ACTIVE|INACTIVE)$") String status,
            @RequestParam(defaultValue = "COUNTRY")
            @Pattern(regexp = "^(COUNTRY|NAME|LONGEST|SHORTEST|EASIEST|HARDEST|NEWEST)$") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(generatedContentService.findQuestions(
                countryCode.toUpperCase(Locale.ROOT), search.strip(), status, sort, page, size));
    }
}
