package io.clueterra.api.admin.budgetcontent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoCandidateDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoDraftDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationResult;
import io.clueterra.api.admin.budgetcontent.service.BudgetProjectSeoService;
import io.clueterra.api.common.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/budget-content/seo-pages")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Budget project SEO", description = "Editorial publication of project pages")
public class AdminBudgetProjectSeoController {

    private final BudgetProjectSeoService service;

    @GetMapping
    @Operation(summary = "List project SEO drafts and publications")
    public ResponseEntity<PageResponse<BudgetProjectSeoPageDto>> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 120) String search,
            @RequestParam(defaultValue = "")
            @Pattern(regexp = "^(|DRAFT|PUBLISHED)$")
            String publicationStatus) {
        return ResponseEntity.ok(service.findPage(page, size, search, publicationStatus));
    }

    @GetMapping("/candidates")
    @Operation(summary = "List reviewed projects available to the SEO editor")
    public ResponseEntity<PageResponse<BudgetProjectSeoCandidateDto>> candidates(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 120) String search,
            @RequestParam(defaultValue = "MISSING")
            @Pattern(regexp = "^(|MISSING|DRAFT|PUBLISHED)$")
            String pageStatus) {
        return ResponseEntity.ok(service.findCandidates(page, size, search, pageStatus));
    }

    @GetMapping("/candidates/{wikidataId}/suggestion")
    @Operation(summary = "Build an editable SEO draft from reviewed project data")
    public ResponseEntity<BudgetProjectSeoDraftDto> suggestion(
            @PathVariable
            @Pattern(regexp = "^Q[1-9][0-9]*$")
            String wikidataId) {
        return ResponseEntity.ok(service.suggestDraft(wikidataId));
    }

    @PostMapping("/{wikidataId}/validate")
    @Operation(summary = "Validate a project page before publication")
    public ResponseEntity<BudgetProjectSeoValidationResult> validate(
            @PathVariable
            @Pattern(regexp = "^Q[1-9][0-9]*$")
            String wikidataId,
            @Valid @RequestBody BudgetProjectSeoPageRequest request) {
        return ResponseEntity.ok(service.validate(wikidataId, request));
    }

    @PutMapping("/{wikidataId}")
    @Operation(summary = "Create or update a project SEO page")
    public ResponseEntity<BudgetProjectSeoPageDto> save(
            @PathVariable
            @Pattern(regexp = "^Q[1-9][0-9]*$")
            String wikidataId,
            @Valid @RequestBody BudgetProjectSeoPageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.save(wikidataId, request, authentication.getName()));
    }
}
