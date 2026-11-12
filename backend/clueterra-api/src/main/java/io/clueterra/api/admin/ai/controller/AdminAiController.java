package io.clueterra.api.admin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.clueterra.api.admin.ai.dto.AiChangeSetActionResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDetailResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDto;
import io.clueterra.api.admin.ai.dto.AiRunDetailResponse;
import io.clueterra.api.admin.ai.dto.AiRunDto;
import io.clueterra.api.admin.ai.service.AdminAiService;
import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/ai")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - AI", description = "AI administration endpoints")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService aiService;
    private final ClueterraQuestionsClient questionsClient;

    @PostMapping("/review/start")
    @Operation(summary = "Start an AI review run")
    public ResponseEntity<JsonNode> startReview(@AuthenticationPrincipal UserPrincipal principal) {
        String uri = requestedByUri("/api/admin/ai/review/start", principal);
        return ResponseEntity.ok(questionsClient.postJson(uri).block());
    }

    @PostMapping("/embedding/backfill")
    @Operation(summary = "Run one AI hint embedding backfill pass")
    public ResponseEntity<JsonNode> backfillEmbeddings(@AuthenticationPrincipal UserPrincipal principal) {
        String uri = requestedByUri("/api/admin/ai/embedding/backfill", principal);
        return ResponseEntity.ok(questionsClient.postJson(uri).block());
    }

    @PostMapping("/generation/start")
    @Operation(summary = "Start an AI generation run")
    public ResponseEntity<JsonNode> startGeneration(@AuthenticationPrincipal UserPrincipal principal) {
        String uri = requestedByUri("/api/admin/ai/generation/start", principal);
        return ResponseEntity.ok(questionsClient.postJson(uri).block());
    }

    @GetMapping("/overview")
    @Operation(summary = "Get configured AI lanes together with live quota usage")
    public ResponseEntity<JsonNode> getOverview() {
        return ResponseEntity.ok(questionsClient.getJson("/api/admin/ai/overview").block());
    }

    @GetMapping("/runs")
    @Operation(summary = "List recent AI runs")
    public ResponseEntity<PageResponse<AiRunDto>> getRuns(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(aiService.getRecentRuns(page, size));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get AI run details")
    public ResponseEntity<AiRunDetailResponse> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(aiService.getRunDetail(runId));
    }

    @GetMapping("/change-sets")
    @Operation(summary = "List recent AI change sets")
    public ResponseEntity<PageResponse<AiChangeSetDto>> getChangeSets(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(aiService.getRecentChangeSets(page, size));
    }

    @GetMapping("/change-sets/{changeSetId}")
    @Operation(summary = "Get AI change set details")
    public ResponseEntity<AiChangeSetDetailResponse> getChangeSet(@PathVariable Long changeSetId) {
        return ResponseEntity.ok(aiService.getChangeSetDetail(changeSetId));
    }

    @PostMapping("/change-sets/{changeSetId}/items/{itemId}/approve")
    @Operation(summary = "Mark an AI change item to be applied")
    public ResponseEntity<AiChangeSetActionResponse> approveItem(
            @PathVariable Long changeSetId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(aiService.approveItem(changeSetId, itemId));
    }

    @PostMapping("/change-sets/{changeSetId}/items/{itemId}/reject")
    @Operation(summary = "Reject an AI change item")
    public ResponseEntity<AiChangeSetActionResponse> rejectItem(
            @PathVariable Long changeSetId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(aiService.rejectItem(changeSetId, itemId));
    }

    @PostMapping("/change-sets/{changeSetId}/apply")
    @Operation(summary = "Apply an AI change set")
    public ResponseEntity<JsonNode> apply(@PathVariable Long changeSetId) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/ai/change-sets/{changeSetId}/apply")
                .buildAndExpand(changeSetId)
                .toUriString();
        return ResponseEntity.ok(questionsClient.postJson(uri).block());
    }

    @PostMapping("/change-sets/{changeSetId}/reject-all")
    @Operation(summary = "Reject all items in an AI change set")
    public ResponseEntity<AiChangeSetActionResponse> rejectAll(
            @PathVariable Long changeSetId,
            @RequestParam(required = false) List<String> itemTypes) {
        return ResponseEntity.ok(aiService.rejectAll(changeSetId, itemTypes));
    }

    @PostMapping("/change-sets/{changeSetId}/approve-all")
    @Operation(summary = "Approve selected AI change item types")
    public ResponseEntity<AiChangeSetActionResponse> approveAll(
            @PathVariable Long changeSetId,
            @RequestParam(required = false) List<String> itemTypes) {
        return ResponseEntity.ok(aiService.approveAll(changeSetId, itemTypes));
    }

    private static String requestedByUri(String path, UserPrincipal principal) {
        return UriComponentsBuilder.fromPath(path)
                .queryParamIfPresent("requestedBy", Optional.ofNullable(principal).map(UserPrincipal::getEmail))
                .toUriString();
    }
}
