package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationImageInventory;
import io.clueterra.questions.ai.model.generation.AiGenerationSpecializedImageRequest;
import io.clueterra.questions.ai.repository.AiGenerationGapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiGenerationSpecializedImagePlanner {

    private final AiProperties aiProperties;
    private final AiGenerationGapRepository generationGapRepository;
    private final Clock clock;

    public Map<Long, AiGenerationSpecializedImageRequest> plan(
            List<AiGenerationCountryGapCandidate> countries) {
        Map<Long, AiGenerationSpecializedImageRequest> requests = new LinkedHashMap<>();
        countries.forEach(country -> requests.put(country.countryId(), plan(country.countryId())));
        return requests;
    }

    private AiGenerationSpecializedImageRequest plan(Long countryId) {
        AiGenerationImageInventory inventory = generationGapRepository.findImageInventoryByCountryId(countryId);
        int landmarkCount = shouldRequest(
                inventory.landmarkCount(),
                aiProperties.getGeneration().getTargetLandmarkImageHintsPerCountry(),
                inventory.lastRejectedLandmarkAt()) ? 1 : 0;
        int currencyCount = shouldRequest(
                inventory.currencyCount(),
                aiProperties.getGeneration().getTargetCurrencyImageHintsPerCountry(),
                inventory.lastRejectedCurrencyAt()) ? 1 : 0;
        return new AiGenerationSpecializedImageRequest(landmarkCount, currencyCount);
    }

    private boolean shouldRequest(int currentCount, int targetCount, Instant lastRejectedAt) {
        if (targetCount <= 0 || currentCount >= targetCount) {
            return false;
        }
        if (lastRejectedAt == null) {
            return true;
        }
        int cooldownDays = Math.max(0, aiProperties.getGeneration().getSpecializedImageRetryCooldownDays());
        Instant retryAt = lastRejectedAt.plus(cooldownDays, ChronoUnit.DAYS);
        return !Instant.now(clock).isBefore(retryAt);
    }
}
