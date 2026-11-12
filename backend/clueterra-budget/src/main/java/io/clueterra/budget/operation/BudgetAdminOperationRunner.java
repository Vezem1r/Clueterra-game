package io.clueterra.budget.operation;

import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Service;

import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationSummary;
import io.clueterra.budget.generation.challenge.service.BudgetDailyChallengeGenerationService;
import io.clueterra.budget.generation.question.model.BudgetQuestionGenerationResult;
import io.clueterra.budget.generation.question.service.BudgetQuestionGenerationService;
import io.clueterra.budget.project.BudgetSyncResult;
import io.clueterra.budget.service.BudgetCandidateEnrichmentService;
import io.clueterra.budget.service.WikidataDiscoveryService;
import io.clueterra.budget.service.console.BudgetConsoleLogWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAdminOperationRunner {

    private final WikidataDiscoveryService discoveryService;
    private final BudgetCandidateEnrichmentService enrichmentService;
    private final BudgetEnrichmentJob enrichmentJob;
    private final BudgetQuestionGenerationService questionGenerationService;
    private final BudgetDailyChallengeGenerationService challengeGenerationService;
    private final BudgetConsoleLogWriter console;

    @Job(name = "BudgetGuessr manual content operation", retries = 0)
    public void run(String operationId, BudgetAdminOperationType operationType) {
        try {
            console.heartbeatOperation(operationId);
            boolean enrichmentQueued =
                    switch (operationType) {
                        case FULL_SYNC -> runFullSync(operationId);
                        case WIKIDATA_DISCOVERY -> runWikidataDiscoveryOnly(operationId);
                        case IMAGE_ENRICHMENT -> runImageEnrichment(operationId);
                        case QUESTION_GENERATION -> runQuestionGeneration();
                        case DAILY_CHALLENGE_GENERATION -> runChallengeGeneration();
                    };
            if (!enrichmentQueued) {
                console.success(operationType.displayName() + " completed");
                console.completeOperation(operationId);
            }
        } catch (RuntimeException exception) {
            String error = conciseMessage(exception);
            console.error(operationType.displayName() + " failed: " + error);
            console.failOperation(operationId, error);
            log.error("[op={}] {} failed", operationId, operationType, exception);
        }
    }

    private boolean runFullSync(String operationId) {
        runWikidataDiscovery(operationId);
        enrichmentJob.enqueueImagePipeline(operationId);
        console.info("Queued licensed image batches for priced Wikidata candidates");
        return true;
    }

    private boolean runWikidataDiscoveryOnly(String operationId) {
        runWikidataDiscovery(operationId);
        console.info("Discovery only: enrichment was not queued");
        return false;
    }

    private void runWikidataDiscovery(String operationId) {
        console.info("Discovering and ranking Wikidata candidates...");
        BudgetSyncResult result =
                discoveryService.synchronize(
                        console::info, () -> console.heartbeatOperation(operationId));
        console.success(
                "Wikidata discovery: "
                        + result.recordsRetained()
                        + "/"
                        + result.recordsDiscovered()
                        + " candidates retained, "
                        + result.categoriesSucceeded()
                        + "/"
                        + result.categoriesAttempted()
                        + " categories");
    }

    private boolean runImageEnrichment(String operationId) {
        int reset = enrichmentService.resetImageFailures();
        console.info("Retrying licensed images for " + reset + " candidates");
        enrichmentJob.enqueueImagePipeline(operationId);
        return true;
    }

    private boolean runQuestionGeneration() {
        BudgetQuestionGenerationResult result = questionGenerationService.generate();
        console.success(
                "Generated "
                        + result.generatedCount()
                        + " playable questions: "
                        + result.byType());
        return false;
    }

    private boolean runChallengeGeneration() {
        BudgetChallengeGenerationSummary result = challengeGenerationService.generateNow();
        console.success(
                "Daily challenges: "
                        + result.createdChallenges()
                        + " created, "
                        + result.skippedChallenges()
                        + " occupied dates skipped ("
                        + result.fromDate()
                        + ".."
                        + result.toDate()
                        + ")");
        return false;
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }
}
