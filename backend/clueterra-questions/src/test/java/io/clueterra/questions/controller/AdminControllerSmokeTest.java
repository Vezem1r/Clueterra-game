package io.clueterra.questions.controller;

import io.clueterra.questions.controller.dto.SourceDetail;
import io.clueterra.questions.controller.dto.SourcesOverview;
import io.clueterra.questions.config.ClockConfig;
import io.clueterra.questions.countryphotos.service.CountryPhotoSyncService;
import io.clueterra.questions.flaggame.service.FlagGenerationService;
import io.clueterra.questions.quizgame.service.QuizGenerationService;
import io.clueterra.questions.silhouettegame.service.SilhouetteGenerationService;
import io.clueterra.questions.exception.OperationInProgressException;
import io.clueterra.questions.image.ImageIntegrityService;
import io.clueterra.questions.rivergame.generation.RiverCatalogGenerationService;
import io.clueterra.questions.rivergame.media.RiverPhotoSyncService;
import io.clueterra.questions.service.challenge.ChallengeRerollService;
import io.clueterra.questions.service.challenge.DailyChallengeGeneratorService;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.source.SourceOverviewService;
import io.clueterra.questions.service.sync.DataSyncOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(ClockConfig.class)
@TestPropertySource(properties = "clueterra.internal.secret=test-secret")
class AdminControllerSmokeTest {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_TOKEN = "test-secret";

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("unused")
    @MockitoBean
    private DataSyncOrchestrator syncOrchestrator;

    @SuppressWarnings("unused")
    @MockitoBean
    private DailyChallengeGeneratorService challengeGenerator;

    @SuppressWarnings("unused")
    @MockitoBean
    private StatisticEnrichmentService enrichmentService;

    @SuppressWarnings("unused")
    @MockitoBean
    private ChallengeRerollService challengeRerollService;

    @SuppressWarnings("unused")
    @MockitoBean
    private SourceOverviewService sourceOverviewService;

    @SuppressWarnings("unused")
    @MockitoBean
    private ImageIntegrityService imageIntegrityService;

    @SuppressWarnings("unused")
    @MockitoBean
    private RiverCatalogGenerationService riverCatalogGenerationService;

    @SuppressWarnings("unused")
    @MockitoBean
    private RiverPhotoSyncService riverPhotoSyncService;

    @MockitoBean
    private CountryPhotoSyncService countryPhotoSyncService;

    @SuppressWarnings("unused")
    @MockitoBean
    private SilhouetteGenerationService silhouetteGenerationService;

    @SuppressWarnings("unused")
    @MockitoBean
    private FlagGenerationService flagGenerationService;

    @SuppressWarnings("unused")
    @MockitoBean
    private QuizGenerationService quizGenerationService;

    @Test
    void countryPhotoSyncStartsAnOperation() throws Exception {
        when(countryPhotoSyncService.startSync()).thenReturn("country-photos-1");

        mockMvc.perform(post("/api/admin/countries/photos/sync").header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").value("country-photos-1"));
    }

    @Test
    void unauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorizedResponseUsesUnifiedErrorBody() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/admin/health"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidRequestParametersReturnBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/admin/challenge/date/not-a-date")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/admin/challenge/date/not-a-date"));
        mockMvc.perform(post("/api/admin/challenge/upcoming").param("days", "100000")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/sync/country/not-a-country")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runningOperationConflictReturns409() throws Exception {
        when(syncOrchestrator.startFullSync()).thenThrow(new OperationInProgressException("Hint Generation"));

        mockMvc.perform(post("/api/admin/sync/full")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Hint Generation")));
    }

    @Test
    void unexpectedIllegalStateIsAServerErrorNotAConflict() throws Exception {
        when(syncOrchestrator.startFullSync()).thenThrow(new IllegalStateException("bug"));

        mockMvc.perform(post("/api/admin/sync/full")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void unauthorizedWhenTokenIsWrong() throws Exception {
        mockMvc.perform(get("/api/admin/health")
                        .header(INTERNAL_TOKEN_HEADER, "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/admin/health")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void overviewEndpointReturnsConfiguredSources() throws Exception {
        SourceDetail curated = new SourceDetail(
                "CURATED_COUNTRY_FACTS", "Curated Country Facts", null, 3,
                true, true, "AVAILABLE", true, true, null, "Available");
        SourceDetail rivers = new SourceDetail(
                "HYDRO_RIVERS", "HydroRIVERS", null, 21,
                true, true, "AVAILABLE", false, true, null, "Available");
        when(sourceOverviewService.getOverview()).thenReturn(new SourcesOverview(
                2, 2, 2, List.of(curated, rivers), Instant.now()));

        mockMvc.perform(get("/api/admin")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSources").value(2))
                .andExpect(content().string(containsString("\"name\":\"CURATED_COUNTRY_FACTS\"")))
                .andExpect(jsonPath("$.sources[?(@.name == 'CURATED_COUNTRY_FACTS')].syncAvailable").value(contains(true)))
                .andExpect(jsonPath("$.sources[?(@.name == 'HYDRO_RIVERS')].hintGenerationAvailable").value(contains(true)))
                .andExpect(jsonPath("$.sources[?(@.name == 'HYDRO_RIVERS')].syncAvailable").value(contains(false)));
    }

    @Test
    void comboHintAllEndpointStartsDedicatedGeneration() throws Exception {
        when(syncOrchestrator.startComboHintGeneration()).thenReturn("combo-op-all");

        mockMvc.perform(post("/api/admin/hints/generate-combo-all")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").value("combo-op-all"))
                .andExpect(jsonPath("$.status").value("STARTED"));

        verify(syncOrchestrator).startComboHintGeneration();
    }

    @Test
    void imageIntegrityEndpointStartsManualAudit() throws Exception {
        when(imageIntegrityService.startManualAudit()).thenReturn("image-audit-op");

        mockMvc.perform(post("/api/admin/images/integrity-audit")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").value("image-audit-op"))
                .andExpect(jsonPath("$.status").value("STARTED"));

        verify(imageIntegrityService).startManualAudit();
    }
}
