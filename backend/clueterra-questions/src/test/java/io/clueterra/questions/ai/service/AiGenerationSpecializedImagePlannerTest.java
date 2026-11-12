package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationImageInventory;
import io.clueterra.questions.ai.model.generation.AiGenerationSpecializedImageRequest;
import io.clueterra.questions.ai.repository.AiGenerationGapRepository;
import io.clueterra.questions.ai.service.generation.AiGenerationSpecializedImagePlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationSpecializedImagePlannerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private AiGenerationGapRepository generationGapRepository;

    private AiProperties aiProperties;
    private AiGenerationSpecializedImagePlanner planner;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.getGeneration().setTargetLandmarkImageHintsPerCountry(3);
        aiProperties.getGeneration().setTargetCurrencyImageHintsPerCountry(2);
        aiProperties.getGeneration().setSpecializedImageRetryCooldownDays(30);
        planner = new AiGenerationSpecializedImagePlanner(
                aiProperties,
                generationGapRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requestsOnlyKindsWhoseInventoryIsBelowTarget() {
        when(generationGapRepository.findImageInventoryByCountryId(1L))
                .thenReturn(new AiGenerationImageInventory(3, 1, null, null));

        Map<Long, AiGenerationSpecializedImageRequest> requests = planner.plan(List.of(country()));

        assertThat(requests.get(1L))
                .isEqualTo(new AiGenerationSpecializedImageRequest(0, 1));
    }

    @Test
    void suppressesOnlyTheRecentlyRejectedKindDuringCooldown() {
        when(generationGapRepository.findImageInventoryByCountryId(1L))
                .thenReturn(new AiGenerationImageInventory(
                        1,
                        0,
                        NOW.minusSeconds(31L * 24 * 60 * 60),
                        NOW.minusSeconds(5L * 24 * 60 * 60)));

        Map<Long, AiGenerationSpecializedImageRequest> requests = planner.plan(List.of(country()));

        assertThat(requests.get(1L))
                .isEqualTo(new AiGenerationSpecializedImageRequest(1, 0));
    }

    private AiGenerationCountryGapCandidate country() {
        return new AiGenerationCountryGapCandidate(1L, "US", "United States", 20, 0, 20, 20, null);
    }
}
