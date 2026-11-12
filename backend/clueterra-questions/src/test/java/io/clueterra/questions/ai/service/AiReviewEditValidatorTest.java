package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewEditValidation;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.review.AiReviewEditValidator;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReviewEditValidatorTest {

    private AiHintEmbeddingService embeddingService;
    private AiReviewEditValidator validator;

    @BeforeEach
    void setUp() {
        embeddingService = mock(AiHintEmbeddingService.class);
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.relatedThreshold()).thenReturn(0.72);
        when(embeddingService.embedAllForGenerationValidation(anyList()))
                .thenReturn(List.of(new float[]{1, 0}, new float[]{1, 0}));
        validator = new AiReviewEditValidator(Optional.of(embeddingService));
    }

    @Test
    void allowsAnchoredParaphraseWithPreservedFacts() {
        AiReviewEditValidation result = validator.validate(
                candidate("The Rhine runs for 1,230 km through this part of Europe."),
                "Trace the Rhine across Europe for all 1,230 km of its course.");

        assertThat(result.autoApplyEligible()).isTrue();
        assertThat(result.semanticSimilarity()).isEqualTo(1.0);
    }

    @Test
    void blocksChangedNumbersAndNewNames() {
        AiReviewEditValidation result = validator.validate(
                candidate("This river runs for 1,230 km."),
                "The Danube runs for 1,240 km through this region.");

        assertThat(result.autoApplyEligible()).isFalse();
        assertThat(result.summary()).contains("numbers changed", "new name outside allowed context");
    }

    @Test
    void blocksTargetCountryLeak() {
        AiReviewEditValidation result = validator.validate(
                candidate("A long river crosses the northern lowlands."),
                "A long river crosses the northern lowlands of Germany.");

        assertThat(result.autoApplyEligible()).isFalse();
        assertThat(result.summary()).contains("target country name in proposed text");
    }

    @Test
    void blocksTargetCountryNameEvenWhenOriginalWasAlreadyLeaking() {
        AiReviewEditValidation result = validator.validate(
                candidate("Germany has a long river."),
                "A long river runs through Germany.");

        assertThat(result.autoApplyEligible()).isFalse();
        assertThat(result.summary()).contains("target country name in proposed text");
    }

    @Test
    void countryNameCheckRespectsWordBoundaries() {
        assertThat(validator.containsTargetCountry("Oman", "A woman sells dates here.")).isFalse();
        assertThat(validator.containsTargetCountry("Oman", "The country of Oman exports dates.")).isTrue();
        assertThat(validator.containsTargetCountry("New Zealand", "Australia and New Zealand")).isTrue();
    }

    @Test
    void reportsUnavailableEmbeddingAsAdvisoryValidationSignal() {
        AiReviewEditValidator unavailableValidator = new AiReviewEditValidator(Optional.empty());

        AiReviewEditValidation result = unavailableValidator.validate(
                candidate("A long river crosses the northern lowlands."),
                "Across the northern lowlands runs a long river.");

        assertThat(result.autoApplyEligible()).isFalse();
        assertThat(result.summary()).contains("local embedding validation unavailable");
    }

    private AiReviewCandidate candidate(String hintText) {
        return new AiReviewCandidate(
                1L,
                2L,
                "DE",
                "Germany",
                "Europe",
                "Western Europe",
                "Berlin",
                BigDecimal.ONE,
                hintText,
                null,
                null,
                50,
                BigDecimal.valueOf(0.8),
                DataSource.CIA_FACTBOOK,
                "CIA_TEST_1",
                2026,
                "revision",
                HintReviewStatus.IN_REVIEW,
                Instant.now(),
                null
        );
    }
}
