package io.clueterra.api.admin.budgetcontent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.budgetcontent.dto.BudgetContentBulkReviewRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentBulkReviewResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCandidateDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCursorPage;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentFacetsResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentIssue;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentTab;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedChallengePage;
import io.clueterra.api.admin.budgetcontent.dto.BudgetChallengePeriod;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedQuestionPage;
import io.clueterra.api.admin.budgetcontent.repository.BudgetContentFilter;
import io.clueterra.api.admin.budgetcontent.repository.BudgetGeneratedContentFilter;
import io.clueterra.api.admin.budgetcontent.service.BudgetContentReviewService;
import io.clueterra.api.admin.budgetcontent.service.BudgetGeneratedContentService;
import io.clueterra.api.budget.dto.BudgetCategory;
import io.clueterra.api.budget.dto.BudgetDifficulty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/budget-content")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Budget content", description = "Editorial review of BudgetGuessr content")
public class AdminBudgetContentController {

    private final BudgetContentReviewService reviewService;
    private final BudgetGeneratedContentService generatedContentService;

    @GetMapping("/candidates")
    @Operation(summary = "Load a cursor-paginated BudgetGuessr review gallery")
    public ResponseEntity<BudgetContentCursorPage> findCandidates(
            @RequestParam(defaultValue = "REVIEW") BudgetContentTab tab,
            @RequestParam(required = false) BudgetCategory category,
            @RequestParam(required = false)
            @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be an ISO alpha-2 code")
            String countryCode,
            @RequestParam(required = false) BudgetDifficulty difficulty,
            @RequestParam(required = false) @Size(max = 150) String search,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minimumQuality,
            @RequestParam(required = false) BudgetContentIssue issue,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int size) {
        BudgetContentFilter filter =
                new BudgetContentFilter(
                        tab,
                        category == null ? null : category.name(),
                        countryCode,
                        difficulty == null ? null : difficulty.name(),
                        search,
                        minimumQuality,
                        issue);
        return ResponseEntity.ok(reviewService.findCandidates(filter, cursor, size));
    }

    @GetMapping("/candidates/{wikidataId}")
    @Operation(summary = "Load complete candidate evidence and image metadata")
    public ResponseEntity<BudgetContentCandidateDto> findCandidate(
            @PathVariable @Pattern(regexp = "^Q[1-9][0-9]*$") String wikidataId,
            @RequestParam BudgetCategory category) {
        return ResponseEntity.ok(reviewService.findDetail(wikidataId, category.name()));
    }

    @PatchMapping("/candidates/review")
    @Operation(summary = "Apply one review decision to selected candidates atomically")
    public ResponseEntity<BudgetContentBulkReviewResponse> reviewSelected(
            @Valid @RequestBody BudgetContentBulkReviewRequest request, Authentication authentication) {
        return ResponseEntity.ok(reviewService.reviewSelected(request, authentication.getName()));
    }

    @PatchMapping("/candidates/{wikidataId}")
    @Operation(summary = "Edit candidate facts and preserve the editorial override across syncs")
    public ResponseEntity<BudgetContentUpdateResponse> updateCandidate(
            @PathVariable @Pattern(regexp = "^Q[1-9][0-9]*$") String wikidataId,
            @RequestParam BudgetCategory category,
            @Valid @RequestBody BudgetContentUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reviewService.updateCandidate(
                wikidataId, category.name(), request, authentication.getName()));
    }

    @GetMapping("/facets")
    @Operation(summary = "Load content counts and geographic quality distribution")
    public ResponseEntity<BudgetContentFacetsResponse> findFacets(
            @RequestParam(defaultValue = "REVIEW") BudgetContentTab tab) {
        return ResponseEntity.ok(reviewService.findFacets(tab));
    }

    @GetMapping("/questions")
    @Operation(summary = "Browse generated BudgetGuessr question variants")
    public ResponseEntity<BudgetGeneratedQuestionPage> findQuestions(
            @RequestParam(defaultValue = "ACTIVE")
            @Pattern(regexp = "^(ALL|ACTIVE|RETIRED)$") String status,
            @RequestParam(required = false)
            @Pattern(regexp = "^(HOW_MUCH|ORIGINAL_BUDGET|BUDGET_OVERRUN|HOW_LATE|HIGHER_LOWER|COST_PER_UNIT|CURRENT_ESTIMATE|SPENT_SO_FAR)$")
            String questionType,
            @RequestParam(required = false) @Size(max = 150) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int size) {
        return ResponseEntity.ok(generatedContentService.findQuestions(
                new BudgetGeneratedContentFilter(status, questionType, search), page, size));
    }

    @GetMapping("/challenges")
    @Operation(summary = "Browse generated BudgetGuessr daily challenges")
    public ResponseEntity<BudgetGeneratedChallengePage> findChallenges(
            @RequestParam(defaultValue = "UPCOMING") BudgetChallengePeriod period,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(60) int size) {
        return ResponseEntity.ok(generatedContentService.findChallenges(period, page, size));
    }

    @DeleteMapping("/challenges/{challengeId}")
    @Operation(summary = "Delete a future BudgetGuessr daily challenge")
    public ResponseEntity<Void> deleteUpcomingChallenge(
            @PathVariable @Min(1) long challengeId) {
        generatedContentService.deleteUpcomingChallenge(challengeId);
        return ResponseEntity.noContent().build();
    }
}
