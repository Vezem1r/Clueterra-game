package io.clueterra.api.admin.quizgame.controller;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.quizgame.dto.QuizStopPreviewPage;
import io.clueterra.api.admin.quizgame.service.QuizGeneratedContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/quiz")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - World quiz", description = "Generated world tour stops, questions and how players answer them")
public class AdminQuizQuestionsController {

    private final QuizGeneratedContentService generatedContentService;

    @GetMapping("/stops")
    @Operation(summary = "Inspect generated world quiz stops, their questions, answers and player accuracy")
    public ResponseEntity<QuizStopPreviewPage> findStops(
            @RequestParam(defaultValue = "") @Pattern(regexp = "^([A-Za-z]{2})?$") String countryCode,
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "^(ALL|ACTIVE|INACTIVE)$") String status,
            @RequestParam(required = false) @Min(1) @Max(5) Integer tier,
            @RequestParam(defaultValue = "ALL")
            @Pattern(regexp = "^(ALL|FLAG|CAPITAL|NEIGHBOUR|CURRENCY|POPULATION|AREA|LEFT_DRIVING|DOMAIN|LANDLOCKED|OUTLINE|COASTLINE|TOURISTS|PEAK|NEIGHBOUR_CAPITAL|HIGHEST_POINT|NOT_NEIGHBOUR|LIFE_EXPECTANCY|WEALTH|HERITAGE|NEIGHBOUR_COUNT|FOREST|MEDIAN_AGE)$")
            String kind,
            @RequestParam(defaultValue = "COUNTRY")
            @Pattern(regexp = "^(COUNTRY|EASIEST|HARDEST|MOST_PLAYED|LOWEST_ACCURACY|NEWEST)$") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int size) {
        return ResponseEntity.ok(generatedContentService.findStops(
                countryCode.toUpperCase(Locale.ROOT), search.strip(), status, tier, kind, sort, page, size));
    }
}
