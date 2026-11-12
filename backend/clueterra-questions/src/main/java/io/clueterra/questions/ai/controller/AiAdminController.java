package io.clueterra.questions.ai.controller;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.controller.mapper.AiAdminResponseMapper;
import io.clueterra.questions.ai.dto.changeset.AiChangeSetActionResponse;
import io.clueterra.questions.ai.dto.overview.AiOverviewResponse;
import io.clueterra.questions.ai.dto.run.AiRunStartResponse;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.service.change.AiChangeSetService;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.generation.AiGenerationQuotaService;
import io.clueterra.questions.ai.service.run.AiRunLaunchService;
import io.clueterra.questions.ai.service.review.AiReviewQuotaService;
import io.clueterra.questions.ai.service.review.AiReviewProgressService;
import io.clueterra.questions.exception.ServiceUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "AI Admin", description = "AI review and generation endpoints")
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiAdminController {

    private final AiRunLaunchService runLaunchService;
    private final AiChangeSetService changeSetService;
    private final AiReviewQuotaService reviewQuotaService;
    private final AiReviewProgressService reviewProgressService;
    private final AiGenerationQuotaService generationQuotaService;
    private final AiProperties aiProperties;
    private final AiAdminResponseMapper responseMapper;
    private final Optional<AiHintEmbeddingService> embeddingService;

    public AiAdminController(
            AiRunLaunchService runLaunchService,
            AiChangeSetService changeSetService,
            AiReviewQuotaService reviewQuotaService,
            AiReviewProgressService reviewProgressService,
            AiGenerationQuotaService generationQuotaService,
            AiProperties aiProperties,
            AiAdminResponseMapper responseMapper,
            Optional<AiHintEmbeddingService> embeddingService) {
        this.runLaunchService = runLaunchService;
        this.changeSetService = changeSetService;
        this.reviewQuotaService = reviewQuotaService;
        this.reviewProgressService = reviewProgressService;
        this.generationQuotaService = generationQuotaService;
        this.aiProperties = aiProperties;
        this.responseMapper = responseMapper;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/review/start")
    @Operation(summary = "Prepare and enqueue an AI review run")
    public ResponseEntity<AiRunStartResponse> startReviewRun(
            @RequestParam(required = false) String requestedBy) {
        AiRunPreparationResult result = runLaunchService.startReview(AiRunTriggerType.MANUAL, requestedBy);
        return ResponseEntity.ok(responseMapper.toRunStartResponse(result));
    }

    @PostMapping("/generation/start")
    @Operation(summary = "Prepare and enqueue an AI generation run")
    public ResponseEntity<AiRunStartResponse> startGenerationRun(
            @RequestParam(required = false) String requestedBy) {
        AiRunPreparationResult result = runLaunchService.startGeneration(AiRunTriggerType.MANUAL, requestedBy);
        return ResponseEntity.ok(responseMapper.toRunStartResponse(result));
    }

    @PostMapping("/embedding/backfill")
    @Operation(summary = "Run one embedding backfill pass over hints missing a vector")
    public ResponseEntity<AiRun> backfillEmbeddings(@RequestParam(required = false) String requestedBy) {
        AiHintEmbeddingService service = embeddingService
                .orElseThrow(() -> new ServiceUnavailableException("Local embedding service is disabled"));
        return ResponseEntity.ok(service.backfillPending(AiRunTriggerType.MANUAL, requestedBy));
    }

    @GetMapping("/overview")
    @Operation(summary = "Get configured AI lanes together with live quota usage")
    public ResponseEntity<AiOverviewResponse> getOverview() {
        return ResponseEntity.ok(responseMapper.toOverview(
                aiProperties,
                aiProperties.getReview(),
                aiProperties.getGeneration(),
                reviewQuotaService.getSnapshot(),
                reviewProgressService.getCoverage(),
                generationQuotaService.getSnapshot(),
                aiProperties.getEmbedding(),
                embeddingService.map(AiHintEmbeddingService::coverage).orElse(null),
                embeddingService.map(AiHintEmbeddingService::isAvailable).orElse(false)
        ));
    }

    @PostMapping("/change-sets/{changeSetId}/apply")
    @Operation(summary = "Apply approved or pending AI change items")
    public ResponseEntity<AiChangeSetActionResponse> applyChangeSet(@PathVariable Long changeSetId) {
        return ResponseEntity.ok(changeSetService.apply(changeSetId));
    }
}
