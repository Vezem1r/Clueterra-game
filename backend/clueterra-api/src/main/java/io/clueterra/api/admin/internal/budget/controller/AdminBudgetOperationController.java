package io.clueterra.api.admin.internal.budget.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.internal.budget.dto.BudgetOperationAcceptedResponse;
import io.clueterra.api.admin.internal.budget.dto.BudgetServiceStatusResponse;
import io.clueterra.api.admin.internal.budget.service.ClueterraBudgetClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/budget-operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Budget operations", description = "BudgetGuessr content synchronization")
public class AdminBudgetOperationController {

    private final ClueterraBudgetClient budgetClient;

    @GetMapping("/status")
    @Operation(summary = "Get BudgetGuessr content service status")
    public ResponseEntity<BudgetServiceStatusResponse> status() {
        return ResponseEntity.ok(budgetClient.getStatus().block());
    }

    @PostMapping("/full")
    @Operation(summary = "Start the complete BudgetGuessr content pipeline")
    public ResponseEntity<BudgetOperationAcceptedResponse> fullSync() {
        return accepted("/internal/budget/operations/full");
    }

    @PostMapping("/wikidata")
    @Operation(summary = "Start BudgetGuessr Wikidata discovery")
    public ResponseEntity<BudgetOperationAcceptedResponse> wikidataDiscovery() {
        return accepted("/internal/budget/operations/wikidata");
    }

    @PostMapping("/images")
    @Operation(summary = "Retry licensed images for priced Wikidata candidates")
    public ResponseEntity<BudgetOperationAcceptedResponse> imageEnrichment() {
        return accepted("/internal/budget/operations/images");
    }

    @PostMapping("/questions")
    @Operation(summary = "Regenerate all playable BudgetGuessr question types")
    public ResponseEntity<BudgetOperationAcceptedResponse> questionGeneration() {
        return accepted("/internal/budget/operations/questions");
    }

    @PostMapping("/challenges")
    @Operation(summary = "Generate BudgetGuessr daily challenges through seven days ahead")
    public ResponseEntity<BudgetOperationAcceptedResponse> dailyChallengeGeneration() {
        return accepted("/internal/budget/operations/challenges");
    }

    private ResponseEntity<BudgetOperationAcceptedResponse> accepted(String path) {
        return ResponseEntity.accepted().body(budgetClient.startOperation(path).block());
    }
}
