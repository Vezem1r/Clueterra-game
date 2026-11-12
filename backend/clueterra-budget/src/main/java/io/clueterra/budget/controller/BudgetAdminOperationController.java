package io.clueterra.budget.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.budget.controller.dto.BudgetOperationAcceptedResponse;
import io.clueterra.budget.operation.BudgetAdminOperationService;
import io.clueterra.budget.operation.BudgetAdminOperationType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/budget/operations")
@RequiredArgsConstructor
@Tag(name = "Budget admin operations", description = "Asynchronous BudgetGuessr content jobs")
public class BudgetAdminOperationController {

    private final BudgetAdminOperationService operationService;

    @PostMapping("/full")
    @Operation(summary = "Start full BudgetGuessr content synchronization")
    public ResponseEntity<BudgetOperationAcceptedResponse> fullSync() {
        return accepted(BudgetAdminOperationType.FULL_SYNC);
    }

    @PostMapping("/wikidata")
    @Operation(summary = "Start Wikidata candidate discovery")
    public ResponseEntity<BudgetOperationAcceptedResponse> wikidataDiscovery() {
        return accepted(BudgetAdminOperationType.WIKIDATA_DISCOVERY);
    }

    @PostMapping("/images")
    @Operation(summary = "Retry licensed images for priced Wikidata candidates")
    public ResponseEntity<BudgetOperationAcceptedResponse> imageEnrichment() {
        return accepted(BudgetAdminOperationType.IMAGE_ENRICHMENT);
    }

    @PostMapping("/questions")
    @Operation(summary = "Regenerate all playable BudgetGuessr question types")
    public ResponseEntity<BudgetOperationAcceptedResponse> questionGeneration() {
        return accepted(BudgetAdminOperationType.QUESTION_GENERATION);
    }

    @PostMapping("/challenges")
    @Operation(summary = "Generate daily challenges from today through seven days ahead")
    public ResponseEntity<BudgetOperationAcceptedResponse> dailyChallengeGeneration() {
        return accepted(BudgetAdminOperationType.DAILY_CHALLENGE_GENERATION);
    }

    private ResponseEntity<BudgetOperationAcceptedResponse> accepted(
            BudgetAdminOperationType operationType) {
        return ResponseEntity.accepted().body(operationService.start(operationType));
    }
}
