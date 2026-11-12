package io.clueterra.questions.ai.service.embedding;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.embedding.AiEmbeddingProperties;
import io.clueterra.questions.ai.model.embedding.AiEmbeddingCoverage;
import io.clueterra.questions.ai.model.embedding.HintEmbeddingValue;
import io.clueterra.questions.ai.model.embedding.PendingHintEmbedding;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.repository.AiRunRepository;
import io.clueterra.questions.ai.repository.HintEmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai.embedding", name = "enabled", havingValue = "true")
public class AiHintEmbeddingService {

    private final AiEmbeddingProperties properties;
    private final HintEmbeddingRepository embeddingRepository;
    private final AiRunRepository runRepository;
    private final AiProperties aiProperties;
    private final LocalEmbeddingClient embeddingClient;
    private final Clock clock;

    public AiHintEmbeddingService(
            AiProperties aiProperties,
            HintEmbeddingRepository embeddingRepository,
            AiRunRepository runRepository,
            LocalEmbeddingClient embeddingClient,
            Clock clock) {
        this.properties = aiProperties.getEmbedding();
        this.embeddingRepository = embeddingRepository;
        this.runRepository = runRepository;
        this.aiProperties = aiProperties;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    public List<float[]> embedAllForGenerationValidation(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(StringUtils::isBlank)) {
            return List.of();
        }
        try {
            return requestEmbeddings(texts);
        } catch (RuntimeException exception) {
            log.warn("Local embedding failed for generation validation", exception);
            return List.of();
        }
    }

    public Map<Long, float[]> vectorsForCountry(Long countryId) {
        return embeddingRepository.findByCountryId(
                countryId,
                properties.getModel(),
                properties.getOutputDimensionality());
    }

    public double duplicateThreshold() {
        return properties.getDuplicateThreshold();
    }

    public double relatedThreshold() {
        return properties.getRelatedThreshold();
    }

    public boolean isAvailable() {
        return embeddingClient.isAvailable();
    }

    public void storeForHint(Long hintId, String hintText, String contentRevisionHash) {
        if (StringUtils.isBlank(hintText) || StringUtils.isBlank(contentRevisionHash)) {
            return;
        }
        try {
            List<float[]> vectors = requestEmbeddings(List.of(hintText));
            embeddingRepository.upsert(
                    new HintEmbeddingValue(hintId, vectors.getFirst(), contentRevisionHash),
                    properties.getModel());
        } catch (RuntimeException exception) {
            log.warn("Local embedding failed for hint {} and will be retried by backfill: {}",
                    hintId, exception.getMessage());
        }
    }

    public int backfillCountry(Long countryId) {
        List<PendingHintEmbedding> pending = embeddingRepository.findHintsPendingEmbedding(
                countryId,
                properties.getModel(),
                properties.getOutputDimensionality());
        int stored = 0;
        for (int from = 0; from < pending.size(); from += properties.getBatchSize()) {
            int to = Math.min(from + properties.getBatchSize(), pending.size());
            List<PendingHintEmbedding> chunk = pending.subList(from, to);
            List<float[]> vectors = requestEmbeddings(
                    chunk.stream().map(PendingHintEmbedding::hintText).toList());
            List<HintEmbeddingValue> values = new ArrayList<>(chunk.size());
            for (int index = 0; index < chunk.size(); index++) {
                PendingHintEmbedding hint = chunk.get(index);
                values.add(new HintEmbeddingValue(
                        hint.hintId(),
                        vectors.get(index),
                        hint.contentRevisionHash()));
            }
            embeddingRepository.upsertBatch(values, properties.getModel());
            stored += values.size();
        }
        if (stored > 0) {
            log.info("Stored {} missing embedding vector(s) for countryId={} before AI generation",
                    stored, countryId);
        }
        return stored;
    }

    public AiEmbeddingCoverage coverage() {
        return embeddingRepository.coverage(properties.getModel(), properties.getOutputDimensionality());
    }

    public AiRun backfillPending(AiRunTriggerType triggerType, String requestedBy) {
        AiEmbeddingCoverage before = coverage();
        if (before.missingHints() == 0) {
            return runRepository.save(buildRun(null, triggerType, requestedBy, AiRunStatus.NO_WORK,
                    before, 0, 0, 0, "No hints pending embedding", null));
        }
        if (!embeddingClient.isAvailable()) {
            String error = "Local embedding service is unavailable";
            return runRepository.save(buildRun(null, triggerType, requestedBy, AiRunStatus.FAILED,
                    before, 0, 0, 0, error, error));
        }

        AiRun run = runRepository.save(buildRun(null, triggerType, requestedBy, AiRunStatus.RUNNING,
                before, 0, 0, 0, "Local embedding backfill running", null));

        int stored = 0;
        int attempted = 0;
        int requests = 0;
        String failure = null;
        List<Long> countries = embeddingRepository.findCountriesPendingEmbedding(
                properties.getBackfillCountriesPerRun(),
                properties.getModel(),
                properties.getOutputDimensionality());
        for (Long countryId : countries) {
            if (attempted >= properties.getBackfillContentsPerRun() || failure != null) {
                break;
            }
            List<PendingHintEmbedding> pending = embeddingRepository.findHintsPendingEmbedding(
                    countryId,
                    properties.getModel(),
                    properties.getOutputDimensionality());
            for (int from = 0;
                 from < pending.size() && attempted < properties.getBackfillContentsPerRun();
                 from += properties.getBatchSize()) {
                int remaining = properties.getBackfillContentsPerRun() - attempted;
                int to = Math.min(from + Math.min(properties.getBatchSize(), remaining), pending.size());
                List<PendingHintEmbedding> chunk = pending.subList(from, to);
                requests++;
                attempted += chunk.size();
                try {
                    List<float[]> vectors = requestEmbeddings(
                            chunk.stream().map(PendingHintEmbedding::hintText).toList());
                    List<HintEmbeddingValue> values = new ArrayList<>(chunk.size());
                    for (int index = 0; index < chunk.size(); index++) {
                        PendingHintEmbedding hint = chunk.get(index);
                        values.add(new HintEmbeddingValue(
                                hint.hintId(),
                                vectors.get(index),
                                hint.contentRevisionHash()));
                    }
                    embeddingRepository.upsertBatch(values, properties.getModel());
                    stored += values.size();
                } catch (RuntimeException exception) {
                    failure = exception.getMessage();
                    log.warn("Local embedding backfill stopped after {} stored vectors: {}", stored, failure);
                    break;
                }
            }
        }

        AiEmbeddingCoverage after = coverage();
        AiRunStatus status = failure == null ? AiRunStatus.COMPLETED : AiRunStatus.FAILED;
        String message = failure == null
                ? "Stored " + stored + " vectors, " + after.missingHints() + " hints still pending"
                : "Stored " + stored + " vectors before local embedding failure";
        log.info("Local embedding backfill stored {} vectors in {} request(s); {} hints still pending",
                stored, requests, after.missingHints());
        return runRepository.save(buildRun(run.id(), triggerType, requestedBy, status,
                after, attempted, stored, requests, message, failure));
    }

    private List<float[]> requestEmbeddings(List<String> texts) {
        List<float[]> vectors = embeddingClient.embed(texts);
        boolean invalidDimensions = vectors.stream()
                .anyMatch(vector -> vector.length != properties.getOutputDimensionality());
        if (vectors.size() != texts.size() || invalidDimensions) {
            throw new IllegalStateException("Local embedding service returned an invalid response for "
                    + texts.size() + " texts");
        }
        return vectors;
    }

    private AiRun buildRun(
            Long id,
            AiRunTriggerType triggerType,
            String requestedBy,
            AiRunStatus status,
            AiEmbeddingCoverage coverage,
            int attempted,
            int stored,
            int requestsUsed,
            String message,
            String errorMessage) {
        Instant now = Instant.now(clock);
        return new AiRun(
                id,
                AiRunType.EMBEDDING,
                triggerType,
                status,
                properties.getProvider(),
                properties.getModel(),
                aiProperties.getPromptVersion(),
                requestedBy,
                requestsUsed,
                attempted,
                attempted,
                stored,
                stored,
                0,
                0,
                0,
                0,
                requestsUsed,
                runConfiguration(),
                runSummary(coverage, message),
                errorMessage,
                now,
                now,
                now);
    }

    private Map<String, Object> runConfiguration() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("outputDimensionality", properties.getOutputDimensionality());
        configuration.put("batchSize", properties.getBatchSize());
        configuration.put("backfillContentsPerRun", properties.getBackfillContentsPerRun());
        configuration.put("backfillCountriesPerRun", properties.getBackfillCountriesPerRun());
        configuration.put("candidateThreshold", properties.getCandidateThreshold());
        configuration.put("duplicateThreshold", properties.getDuplicateThreshold());
        configuration.put("relatedThreshold", properties.getRelatedThreshold());
        return configuration;
    }

    private static Map<String, Object> runSummary(AiEmbeddingCoverage coverage, String message) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", message);
        summary.put("activeHints", coverage.activeHints());
        summary.put("embeddedHints", coverage.embeddedHints());
        summary.put("missingHints", coverage.missingHints());
        summary.put("coveragePercent", Math.round(coverage.coveragePercent() * 10) / 10.0);
        return summary;
    }
}
