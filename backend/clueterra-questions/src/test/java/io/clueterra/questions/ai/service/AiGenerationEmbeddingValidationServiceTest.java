package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationEmbeddingValidationResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationProposalResult;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.generation.AiGenerationEmbeddingValidationService;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.repository.HintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationEmbeddingValidationServiceTest {

    private static final long COUNTRY_ID = 41L;
    private static final long HINT_ID = 73L;

    @Mock
    private HintRepository hintRepository;
    @Mock
    private AiExecutionRepository executionRepository;
    @Mock
    private AiHintEmbeddingService embeddingService;

    private AiGenerationEmbeddingValidationService service;
    private AiGenerationCountryGapCandidate country;

    @BeforeEach
    void setUp() {
        service = new AiGenerationEmbeddingValidationService(
                hintRepository,
                executionRepository,
                Optional.of(embeddingService));
        country = new AiGenerationCountryGapCandidate(
                COUNTRY_ID, "ES", "Spain", 50, 0, 50, 0, null);
    }

    @Test
    void marksSemanticMatchAgainstActiveHintAsConflict() {
        String draft = "A midnight ritual uses a dozen pieces of fruit for luck.";
        Hint existing = Hint.builder()
                .id(HINT_ID)
                .countryId(COUNTRY_ID)
                .hintText("People eat twelve grapes as the clock strikes on New Year's Eve.")
                .build();
        AiHintGenerationBatchResult batch = batch(draft);

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embedAllForGenerationValidation(List.of(draft)))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}));
        when(embeddingService.relatedThreshold()).thenReturn(0.72);
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of(existing));
        when(embeddingService.vectorsForCountry(COUNTRY_ID))
                .thenReturn(Map.of(HINT_ID, new float[]{0.99f, 0.01f}));
        when(executionRepository.findProposalSummariesByCountryId(COUNTRY_ID, 30)).thenReturn(List.of());

        AiGenerationEmbeddingValidationResult result = service.validate(List.of(country), batch);

        assertThat(result.hasUnchecked()).isFalse();
        assertThat(result.conflicts()).singleElement().satisfies(assessment -> {
            assertThat(assessment.proposalIndex()).isZero();
            assertThat(assessment.highestSimilarity()).isGreaterThan(0.99);
            assertThat(assessment.matches()).singleElement()
                    .satisfies(match -> assertThat(match.sourceType()).isEqualTo("ACTIVE_HINT"));
        });
    }

    @Test
    void checksWholeBatchAndFlagsOnlyLaterDuplicate() {
        String first = "A high court classified a famous orange snack as cake for tax purposes.";
        String second = "Tax judges decided that an orange-coated snack was legally a cake.";
        AiHintGenerationBatchResult batch = new AiHintGenerationBatchResult(
                "batch",
                List.of(proposal(first), proposal(second)));

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embedAllForGenerationValidation(List.of(first, second)))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}, new float[]{1.0f, 0.0f}));
        when(embeddingService.relatedThreshold()).thenReturn(0.72);
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of());
        when(embeddingService.vectorsForCountry(COUNTRY_ID)).thenReturn(Map.of());
        when(executionRepository.findProposalSummariesByCountryId(COUNTRY_ID, 30)).thenReturn(List.of());

        AiGenerationEmbeddingValidationResult result = service.validate(List.of(country), batch);

        assertThat(result.assessments()).hasSize(2);
        assertThat(result.assessments().getFirst().conflict()).isFalse();
        assertThat(result.conflicts()).singleElement().satisfies(assessment -> {
            assertThat(assessment.proposalIndex()).isEqualTo(1);
            assertThat(assessment.matches()).singleElement().satisfies(match -> {
                assertThat(match.sourceType()).isEqualTo("CURRENT_BATCH");
                assertThat(match.sourceProposalIndex()).isZero();
            });
        });
    }

    @Test
    void usesRelatedThresholdForRepairAndDuplicateThresholdForPersistence() {
        String draft = "A related but distinct cultural clue.";
        Hint existing = Hint.builder()
                .id(HINT_ID)
                .countryId(COUNTRY_ID)
                .hintText("An existing clue from the same broad cultural theme.")
                .build();

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embedAllForGenerationValidation(List.of(draft)))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}));
        when(embeddingService.relatedThreshold()).thenReturn(0.72);
        when(embeddingService.duplicateThreshold()).thenReturn(0.88);
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of(existing));
        when(embeddingService.vectorsForCountry(COUNTRY_ID))
                .thenReturn(Map.of(HINT_ID, new float[]{0.8f, 0.6f}));
        when(executionRepository.findProposalSummariesByCountryId(COUNTRY_ID, 30)).thenReturn(List.of());

        assertThat(service.validate(List.of(country), batch(draft)).conflicts()).hasSize(1);
        assertThat(service.validateForPersistence(List.of(country), batch(draft)).conflicts()).isEmpty();
    }

    @Test
    void failsClosedWhenEmbeddingServiceIsUnavailable() {
        String draft = "A vivid and otherwise valid country clue.";
        when(embeddingService.isAvailable()).thenReturn(false);

        AiGenerationEmbeddingValidationResult result = service.validate(List.of(country), batch(draft));

        assertThat(result.hasUnchecked()).isTrue();
        assertThat(result.assessments()).singleElement().satisfies(assessment -> {
            assertThat(assessment.checked()).isFalse();
            assertThat(assessment.validationSummary()).contains("unavailable");
        });
    }

    @Test
    void failsClosedWhenAnActiveHintHasNoStoredVector() {
        String draft = "A vivid and otherwise valid country clue.";
        Hint existing = Hint.builder()
                .id(HINT_ID)
                .countryId(COUNTRY_ID)
                .hintText("An existing active clue without a vector.")
                .build();

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embedAllForGenerationValidation(List.of(draft)))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}));
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of(existing));
        when(embeddingService.vectorsForCountry(COUNTRY_ID)).thenReturn(Map.of());
        when(executionRepository.findProposalSummariesByCountryId(COUNTRY_ID, 30)).thenReturn(List.of());

        AiGenerationEmbeddingValidationResult result = service.validate(List.of(country), batch(draft));

        assertThat(result.hasUnchecked()).isTrue();
        assertThat(result.assessments()).singleElement()
                .satisfies(assessment -> assertThat(assessment.validationSummary())
                        .contains("coverage is incomplete"));
    }

    @Test
    void repairsMissingCountryCoverageBeforeGeneration() {
        Hint existing = Hint.builder()
                .id(HINT_ID)
                .countryId(COUNTRY_ID)
                .hintText("An existing active clue awaiting its vector.")
                .build();

        when(embeddingService.isAvailable()).thenReturn(true);
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of(existing));
        when(embeddingService.vectorsForCountry(COUNTRY_ID))
                .thenReturn(Map.of(), Map.of(HINT_ID, new float[]{1.0f, 0.0f}));
        when(embeddingService.backfillCountry(COUNTRY_ID)).thenReturn(1);

        service.requireReady(List.of(country));

        verify(embeddingService).backfillCountry(COUNTRY_ID);
    }

    @Test
    void stillFailsClosedWhenTargetedBackfillCannotCompleteCoverage() {
        Hint existing = Hint.builder()
                .id(HINT_ID)
                .countryId(COUNTRY_ID)
                .hintText("An existing active clue awaiting its vector.")
                .build();

        when(embeddingService.isAvailable()).thenReturn(true);
        when(hintRepository.findActiveByCountryId(COUNTRY_ID)).thenReturn(List.of(existing));
        when(embeddingService.vectorsForCountry(COUNTRY_ID)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.requireReady(List.of(country)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete active-hint coverage")
                .hasMessageContaining(String.valueOf(COUNTRY_ID));

        verify(embeddingService).backfillCountry(COUNTRY_ID);
    }

    private AiHintGenerationBatchResult batch(String hintText) {
        return new AiHintGenerationBatchResult("batch", List.of(proposal(hintText)));
    }

    private AiHintGenerationProposalResult proposal(String hintText) {
        return new AiHintGenerationProposalResult(
                COUNTRY_ID,
                "TRIVIA",
                "ABSOLUTE_FACT",
                hintText,
                null,
                null,
                55,
                78.0,
                "fact",
                "evidence",
                "distinct angle",
                List.of("keyword"));
    }
}
