package io.clueterra.api.admin.internal.questions.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.clueterra.api.admin.challenges.service.ChallengeService;
import io.clueterra.api.admin.internal.questions.dto.ChallengeRerollResponse;
import io.clueterra.api.admin.internal.questions.dto.RerollCountryRequest;
import io.clueterra.api.admin.internal.questions.dto.RerollHintRequest;
import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Sync", description = "Data sync with questions service")
@Slf4j
public class AdminQuestionsController {

    private static final Map<String, String> ACCEPTED = Map.of("status", "ACCEPTED");

    private final ClueterraQuestionsClient questionsClient;
    private final ChallengeService challengeService;

    @Operation(summary = "Start manual generation of river game questions from the verified catalog")
    @PostMapping("/rivers/generate")
    public ResponseEntity<JsonNode> generateRiverQuestions() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/rivers/generate").block());
    }

    @Operation(summary = "Start syncing river answer photos and Wikipedia links")
    @PostMapping("/rivers/photos/sync")
    public ResponseEntity<JsonNode> syncRiverPhotos() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/rivers/photos/sync").block());
    }

    @Operation(summary = "Start choosing and storing the best-known landmark photo for every country")
    @PostMapping("/countries/photos/sync")
    public ResponseEntity<JsonNode> syncCountryPhotos() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/countries/photos/sync").block());
    }

    @Operation(summary = "Start generating country silhouette questions and clues from the catalog and verified statistics")
    @PostMapping("/silhouettes/generate")
    public ResponseEntity<JsonNode> generateSilhouetteQuestions() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/silhouettes/generate").block());
    }

    @Operation(summary = "Start generating guess-the-flag questions and clues from the catalog and verified statistics")
    @PostMapping("/flags/generate")
    public ResponseEntity<JsonNode> generateFlagQuestions() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/flags/generate").block());
    }

    @Operation(summary = "Start generating world quiz stops and multiple-choice questions from the catalog and verified statistics")
    @PostMapping("/quiz/generate")
    public ResponseEntity<JsonNode> generateQuizQuestions() {
        return ResponseEntity.accepted().body(questionsClient.postJson("/api/admin/quiz/generate").block());
    }

    @PostMapping("/sync/full")
    public ResponseEntity<Map<String, String>> syncAll() {
        questionsClient.fireAndForget("Full sync", "/api/admin/sync/full");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/sync/source/{source}")
    public ResponseEntity<Map<String, String>> syncFromSource(@NotBlank @PathVariable String source) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/sync/source/{source}")
                .buildAndExpand(source).toUriString();
        questionsClient.fireAndForget("Source sync " + source, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/sync/country/{countryCode}")
    public ResponseEntity<Map<String, String>> syncCountry(@NotBlank @PathVariable String countryCode) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/sync/country/{code}")
                .buildAndExpand(countryCode).toUriString();
        questionsClient.fireAndForget("Sync " + countryCode, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/sync/source/{source}/country/{countryCode}")
    public ResponseEntity<Map<String, String>> syncCountryFromSource(
            @NotBlank @PathVariable String source, @NotBlank @PathVariable String countryCode) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/sync/source/{source}/country/{code}")
                .buildAndExpand(source, countryCode).toUriString();
        questionsClient.fireAndForget("Sync " + countryCode + " from " + source, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/enrich")
    public ResponseEntity<Map<String, String>> enrichStatistics() {
        questionsClient.fireAndForget("Enrichment", "/api/admin/enrich");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/hints/generate/{countryCode}")
    public ResponseEntity<Map<String, String>> generateHintsForCountry(
            @NotBlank @PathVariable String countryCode) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/hints/generate/{code}")
                .buildAndExpand(countryCode).toUriString();
        questionsClient.fireAndForget("Generate hints " + countryCode, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/hints/generate/source/{source}")
    public ResponseEntity<Map<String, String>> generateHintsForSource(
            @NotBlank @PathVariable String source) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/hints/generate/source/{source}")
                .buildAndExpand(source).toUriString();
        questionsClient.fireAndForget("Generate hints for source " + source, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/hints/generate-all")
    public ResponseEntity<Map<String, String>> generateHintsForAllCountries() {
        questionsClient.fireAndForget("Hint generation", "/api/admin/hints/generate-all");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @Operation(summary = "Generate combinatorics hints for all countries")
    @PostMapping("/hints/generate-combo-all")
    public ResponseEntity<Map<String, String>> generateComboHintsForAllCountries() {
        questionsClient.fireAndForget("Combo hint generation", "/api/admin/hints/generate-combo-all");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @Operation(summary = "Audit source attribution and restore missing question images")
    @PostMapping("/images/integrity-audit")
    public ResponseEntity<Map<String, String>> auditImageIntegrity() {
        questionsClient.fireAndForget("Image integrity audit", "/api/admin/images/integrity-audit");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/challenge/today")
    public ResponseEntity<Map<String, String>> generateTodaysChallenges() {
        questionsClient.fireAndForget("Generate today's challenges", "/api/admin/challenge/today");
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/challenge/date/{date}")
    public ResponseEntity<Map<String, String>> generateChallengesForDate(
            @NotBlank @PathVariable String date, @RequestParam(required = false) String level) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/admin/challenge/date/{date}");
        if (level != null) builder.queryParam("level", level);
        String uri = builder.buildAndExpand(date).toUriString();
        questionsClient.fireAndForget("Generate challenges for " + date, uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/challenge/upcoming")
    public ResponseEntity<Map<String, String>> generateUpcomingChallenges(
            @RequestParam(defaultValue = "7") int days) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/challenge/upcoming")
                .queryParam("days", days).toUriString();
        questionsClient.fireAndForget("Generate upcoming challenges", uri);
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/challenge/{challengeId}/reroll-hint")
    public ResponseEntity<ChallengeRerollResponse> rerollHint(
            @PathVariable Long challengeId,
            @Valid @RequestBody RerollHintRequest request) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/challenge/{id}/reroll-hint")
                .buildAndExpand(challengeId).toUriString();
        ChallengeRerollResponse response = questionsClient
                .postWithBody(uri, request, ChallengeRerollResponse.class).block();
        challengeService.invalidateAfterExternalChallengeMutation();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/challenge/{challengeId}/reroll-country")
    public ResponseEntity<ChallengeRerollResponse> rerollCountry(
            @PathVariable Long challengeId,
            @Valid @RequestBody RerollCountryRequest request) {
        String uri = UriComponentsBuilder.fromPath("/api/admin/challenge/{id}/reroll-country")
                .buildAndExpand(challengeId).toUriString();
        ChallengeRerollResponse response = questionsClient
                .postWithBody(uri, request, ChallengeRerollResponse.class).block();
        challengeService.invalidateAfterExternalChallengeMutation();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(questionsClient.getHealth().block());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSources() {
        return ResponseEntity.ok(questionsClient.getSourcesOverview().block());
    }
}
