package io.clueterra.questions.controller;

import io.clueterra.questions.controller.dto.ChallengeRerollResponse;
import io.clueterra.questions.controller.dto.HealthResponse;
import io.clueterra.questions.controller.dto.OperationStartedResponse;
import io.clueterra.questions.controller.dto.RerollCountryRequest;
import io.clueterra.questions.controller.dto.RerollHintRequest;
import io.clueterra.questions.controller.dto.SourcesOverview;
import io.clueterra.questions.countryphotos.service.CountryPhotoSyncService;
import io.clueterra.questions.flaggame.service.FlagGenerationService;
import io.clueterra.questions.quizgame.service.QuizGenerationService;
import io.clueterra.questions.silhouettegame.service.SilhouetteGenerationService;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.image.ImageIntegrityService;
import io.clueterra.questions.rivergame.generation.RiverCatalogGenerationService;
import io.clueterra.questions.rivergame.media.RiverPhotoSyncService;
import io.clueterra.questions.service.challenge.ChallengeRerollService;
import io.clueterra.questions.service.challenge.DailyChallengeGeneratorService;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.source.SourceOverviewService;
import io.clueterra.questions.service.sync.DataSyncOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Questions Admin", description = "Internal admin operations of the Questions service")
public class AdminController {

    private static final String COUNTRY_CODE = "^[A-Za-z]{2,3}$";
    private static final String SOURCE_NAME = "^[A-Za-z_]+$";

    private final DataSyncOrchestrator syncOrchestrator;
    private final DailyChallengeGeneratorService challengeGenerator;
    private final ChallengeRerollService rerollService;
    private final StatisticEnrichmentService enrichmentService;
    private final ImageIntegrityService imageIntegrityService;
    private final SourceOverviewService sourceOverviewService;
    private final RiverCatalogGenerationService riverCatalogGenerationService;
    private final RiverPhotoSyncService riverPhotoSyncService;
    private final CountryPhotoSyncService countryPhotoSyncService;
    private final SilhouetteGenerationService silhouetteGenerationService;
    private final FlagGenerationService flagGenerationService;
    private final QuizGenerationService quizGenerationService;
    private final Clock clock;

    @Operation(summary = "Import source-backed river questions and maps from the bundled catalog")
    @PostMapping("/rivers/generate")
    public ResponseEntity<OperationStartedResponse> generateRiverQuestions() {
        return started(riverCatalogGenerationService.startGeneration());
    }

    @Operation(summary = "Sync river answer photos and Wikipedia links from Wikidata and Wikimedia Commons")
    @PostMapping("/rivers/photos/sync")
    public ResponseEntity<OperationStartedResponse> syncRiverPhotos() {
        return started(riverPhotoSyncService.startSync());
    }

    @Operation(summary = "Choose and store the best-known landmark photo for every country from Wikimedia Commons")
    @PostMapping("/countries/photos/sync")
    public ResponseEntity<OperationStartedResponse> syncCountryPhotos() {
        return started(countryPhotoSyncService.startSync());
    }

    @Operation(summary = "Generate country silhouette questions and clues from the bundled catalog and verified statistics")
    @PostMapping("/silhouettes/generate")
    public ResponseEntity<OperationStartedResponse> generateSilhouetteQuestions() {
        return started(silhouetteGenerationService.startGeneration());
    }

    @Operation(summary = "Generate guess-the-flag questions and clues from the bundled catalog and verified statistics")
    @PostMapping("/flags/generate")
    public ResponseEntity<OperationStartedResponse> generateFlagQuestions() {
        return started(flagGenerationService.startGeneration());
    }

    @Operation(summary = "Generate world quiz stops and multiple-choice questions from the bundled catalog and verified statistics")
    @PostMapping("/quiz/generate")
    public ResponseEntity<OperationStartedResponse> generateQuizQuestions() {
        return started(quizGenerationService.startGeneration());
    }

    @Operation(summary = "Sync every available source")
    @PostMapping("/sync/full")
    public ResponseEntity<OperationStartedResponse> syncAll() {
        return started(syncOrchestrator.startFullSync());
    }

    @Operation(summary = "Sync one source for all countries")
    @PostMapping("/sync/source/{source}")
    public ResponseEntity<OperationStartedResponse> syncFromSource(
            @PathVariable @Pattern(regexp = SOURCE_NAME) String source) {
        return started(syncOrchestrator.startSourceSync(dataSource(source)));
    }

    @Operation(summary = "Sync one country from every available source")
    @PostMapping("/sync/country/{countryCode}")
    public ResponseEntity<OperationStartedResponse> syncCountry(
            @PathVariable @Pattern(regexp = COUNTRY_CODE) String countryCode) {
        return started(syncOrchestrator.startCountrySync(countryCode));
    }

    @Operation(summary = "Sync one country from one source")
    @PostMapping("/sync/source/{source}/country/{countryCode}")
    public ResponseEntity<OperationStartedResponse> syncCountryFromSource(
            @PathVariable @Pattern(regexp = SOURCE_NAME) String source,
            @PathVariable @Pattern(regexp = COUNTRY_CODE) String countryCode) {
        return started(syncOrchestrator.startCountrySourceSync(dataSource(source), countryCode));
    }

    @Operation(summary = "Recompute comparative statistics enrichment")
    @PostMapping("/enrich")
    public ResponseEntity<OperationStartedResponse> enrichStatistics() {
        return started(enrichmentService.startEnrichment());
    }

    @Operation(summary = "Regenerate hints for one country")
    @PostMapping("/hints/generate/{countryCode}")
    public ResponseEntity<OperationStartedResponse> generateHintsForCountry(
            @PathVariable @Pattern(regexp = COUNTRY_CODE) String countryCode) {
        return started(syncOrchestrator.startCountryHintGeneration(countryCode));
    }

    @Operation(summary = "Regenerate hints of one source for all countries")
    @PostMapping("/hints/generate/source/{source}")
    public ResponseEntity<OperationStartedResponse> generateHintsForSource(
            @PathVariable @Pattern(regexp = SOURCE_NAME) String source) {
        return started(syncOrchestrator.startSourceHintGeneration(source));
    }

    @Operation(summary = "Regenerate hints for all active countries")
    @PostMapping("/hints/generate-all")
    public ResponseEntity<OperationStartedResponse> generateHintsForAllCountries() {
        return started(syncOrchestrator.startHintGeneration());
    }

    @Operation(summary = "Regenerate combinatorics hints for all active countries")
    @PostMapping("/hints/generate-combo-all")
    public ResponseEntity<OperationStartedResponse> generateComboHintsForAllCountries() {
        return started(syncOrchestrator.startComboHintGeneration());
    }

    @Operation(summary = "Audit image storage and source attribution, and restore missing hint images")
    @PostMapping("/images/integrity-audit")
    public ResponseEntity<OperationStartedResponse> auditImageIntegrity() {
        return started(imageIntegrityService.startManualAudit());
    }

    @Operation(summary = "Generate today's challenges for every level")
    @PostMapping("/challenge/today")
    public ResponseEntity<OperationStartedResponse> generateTodaysChallenges() {
        return started(challengeGenerator.startTodayGeneration());
    }

    @Operation(summary = "Generate challenges for a date (all levels or one level)")
    @PostMapping("/challenge/date/{date}")
    public ResponseEntity<OperationStartedResponse> generateChallengesForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String level) {
        ChallengeLevel challengeLevel = level != null ? ChallengeLevel.fromString(level) : null;
        return started(challengeGenerator.startDateGeneration(date, challengeLevel));
    }

    @Operation(summary = "Generate challenges for the upcoming days")
    @PostMapping("/challenge/upcoming")
    public ResponseEntity<OperationStartedResponse> generateUpcomingChallenges(
            @RequestParam(defaultValue = "7") @Min(1) @Max(60) int days) {
        return started(challengeGenerator.startUpcomingGeneration(days));
    }

    @Operation(summary = "Replace one hint in an upcoming challenge")
    @PostMapping("/challenge/{challengeId}/reroll-hint")
    public ResponseEntity<ChallengeRerollResponse> rerollHint(
            @PathVariable Long challengeId,
            @Valid @RequestBody RerollHintRequest request) {
        DailyChallenge updated = rerollService.rerollHint(challengeId, request.countryOrder(), request.hintOrder());
        return ResponseEntity.ok(rerolled(updated));
    }

    @Operation(summary = "Replace one country in a future challenge")
    @PostMapping("/challenge/{challengeId}/reroll-country")
    public ResponseEntity<ChallengeRerollResponse> rerollCountry(
            @PathVariable Long challengeId,
            @Valid @RequestBody RerollCountryRequest request) {
        DailyChallenge updated = rerollService.rerollCountry(challengeId, request.countryOrder());
        return ResponseEntity.ok(rerolled(updated));
    }

    @Operation(summary = "Authenticated liveness check used by clueterra-api")
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.up(clock));
    }

    @Operation(summary = "Overview of configured data sources")
    @GetMapping
    public ResponseEntity<SourcesOverview> getAllSources() {
        return ResponseEntity.ok(sourceOverviewService.getOverview());
    }

    private static DataSource dataSource(String source) {
        return DataSource.valueOf(source.toUpperCase(Locale.ROOT));
    }

    private static ResponseEntity<OperationStartedResponse> started(String operationId) {
        return ResponseEntity.accepted().body(OperationStartedResponse.started(operationId));
    }

    private static ChallengeRerollResponse rerolled(DailyChallenge challenge) {
        return new ChallengeRerollResponse(
                challenge.getId(), "REROLLED", challenge.getTotalCountries(), challenge.getTotalHints());
    }
}
