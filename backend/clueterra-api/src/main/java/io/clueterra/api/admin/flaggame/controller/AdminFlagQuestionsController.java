package io.clueterra.api.admin.flaggame.controller;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.flaggame.dto.FlagQuestionPreviewPage;
import io.clueterra.api.admin.flaggame.service.FlagGeneratedContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/flags")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Flag game", description = "Generated Guess the flag content and play statistics")
public class AdminFlagQuestionsController {

    private final FlagGeneratedContentService generatedContentService;

    @GetMapping("/questions")
    @Operation(summary = "Inspect generated flag questions, their clues and how players do on them")
    public ResponseEntity<FlagQuestionPreviewPage> findQuestions(
            @RequestParam(defaultValue = "") @Pattern(regexp = "^([A-Za-z]{2})?$") String countryCode,
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|ACTIVE|INACTIVE)$") String status,
            @RequestParam(required = false) @Min(1) @Max(5) Integer tier,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|WITH_CAPITAL|NO_CAPITAL)$") String capital,
            @RequestParam(defaultValue = "COUNTRY")
            @Pattern(regexp = "^(COUNTRY|EASIEST|HARDEST|MOST_PLAYED|MOST_SKIPPED|MOST_CONFUSED|NEWEST)$") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(generatedContentService.findQuestions(
                countryCode.toUpperCase(Locale.ROOT), search.strip(), status, tier, capital, sort, page, size));
    }
}
