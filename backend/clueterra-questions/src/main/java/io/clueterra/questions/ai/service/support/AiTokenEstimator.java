package io.clueterra.questions.ai.service.support;

import io.clueterra.questions.ai.config.generation.AiGenerationProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationTokenizationProperties;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.review.AiReviewTokenizationProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AiTokenEstimator {

    private final AiProperties aiProperties;

    public int baseRequestOverheadTokens() {
        return generationTokenization().getBaseRequestOverheadTokens();
    }

    public int reviewBaseRequestOverheadTokens() {
        return reviewTokenization().getBaseRequestOverheadTokens();
    }

    public int targetInputTokensPerRequest() {
        AiGenerationTokenizationProperties tokenization = generationTokenization();
        return (int) Math.floor(tokenization.getMaxInputTokensPerRequest() * tokenization.getTargetInputUtilization());
    }

    public int reviewTargetInputTokensPerRequest() {
        AiReviewTokenizationProperties tokenization = reviewTokenization();
        return (int) Math.floor(tokenization.getMaxInputTokensPerRequest() * tokenization.getTargetInputUtilization());
    }

    public int estimateTextTokens(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .mapToInt(this::estimateTextTokens)
                .sum();
    }

    public int estimateTextTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(value.length() / 4.0));
    }

    public int estimateReviewCandidateTokens(AiReviewCandidate candidate) {
        int tokens = 80;
        tokens += estimateTextTokens(
                candidate.countryCode(),
                candidate.countryName(),
                candidate.hintText(),
                candidate.sourceIndicator()
        );

        if (candidate.source() != null) {
            tokens += estimateTextTokens(candidate.source().name());
        }
        if (candidate.sourceYear() != null) {
            tokens += estimateTextTokens(String.valueOf(candidate.sourceYear()));
        }
        if (candidate.difficulty() != null) {
            tokens += estimateTextTokens(String.valueOf(candidate.difficulty()));
        }
        if (candidate.qualityScore() != null) {
            tokens += estimateTextTokens(candidate.qualityScore().toPlainString());
        }
        if (candidate.hasImage() && reviewTokenization().isAllowImages()) {
            tokens += reviewTokenization().getEstimatedImageTokens();
        }
        return tokens;
    }

    public int estimateGenerationCountryTokens(AiGenerationCountryGapCandidate country) {
        AiGenerationProperties generationProperties = aiProperties.getGeneration();
        int requestedProposalCount = Math.max(
                1,
                Math.min(
                        generationProperties.getRequestedProposalsPerCountry(),
                        Math.max(0, country.missingHintCount())
                )
        );
        int tokens = 450;
        tokens += estimateTextTokens(
                country.countryCode(),
                country.countryName(),
                String.valueOf(country.activeHintCount()),
                String.valueOf(country.pendingProposalCount()),
                String.valueOf(country.effectiveHintCount()),
                String.valueOf(country.missingHintCount()),
                String.valueOf(requestedProposalCount)
        );
        tokens += requestedProposalCount * 180;
        tokens += Math.min(40, Math.max(0, country.activeHintCount()) + Math.max(0, country.pendingProposalCount())) * 35;
        tokens += 1_300;
        return tokens;
    }

    private AiReviewTokenizationProperties reviewTokenization() {
        return aiProperties.getReview().getTokenization();
    }

    private AiGenerationTokenizationProperties generationTokenization() {
        return aiProperties.getGeneration().getTokenization();
    }
}
