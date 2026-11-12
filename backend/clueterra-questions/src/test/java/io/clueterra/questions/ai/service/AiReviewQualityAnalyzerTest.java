package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.service.review.AiReviewQualityAnalyzer;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewQualityAnalyzerTest {

    private final AiReviewQualityAnalyzer analyzer = new AiReviewQualityAnalyzer();

    @Test
    void flagsTemplateHypeMissingPunctuationAndUnderratedOperator() {
        AiReviewCandidate candidate = candidate(
                "Of course you have heard of it — this mega-brand serves billions of people",
                "CITYBIKES_OPERATOR",
                26
        );

        assertThat(analyzer.analyze(candidate)).contains(
                "MISSING_TERMINAL_PUNCTUATION",
                "UNSUPPORTED_HYPERBOLE_OR_ABSOLUTE_CLAIM",
                "GENERIC_AI_VOICE_OR_TEMPLATE",
                "POSSIBLY_UNDERRATED_OBSCURE_NAMED_ENTITY"
        );
    }

    @Test
    void leavesCleanSpecificHintWithoutSignals() {
        AiReviewCandidate candidate = candidate(
                "A local network operates 231 docking stations in the capital.",
                "CITYBIKES_STATIONS",
                52
        );

        assertThat(analyzer.analyze(candidate)).isEmpty();
    }

    @Test
    void flagsBareCommonVotingFactButAcceptsTopicRelevantRegionalContext() {
        AiReviewCandidate bare = contextualCandidate("Voting starts at 18.");
        AiReviewCandidate improved = contextualCandidate(
                "In this Western European country, the ballot opens to citizens at 18."
        );

        assertThat(analyzer.analyze(bare)).contains("BARE_COMMON_FACT_WITHOUT_COUNTRY_CONTEXT");
        assertThat(analyzer.analyze(improved)).doesNotContain("BARE_COMMON_FACT_WITHOUT_COUNTRY_CONTEXT");
    }

    private AiReviewCandidate candidate(String hintText, String indicator, int difficulty) {
        return new AiReviewCandidate(
                1L,
                2L,
                "DE",
                "Germany",
                BigDecimal.ONE,
                hintText,
                null,
                null,
                difficulty,
                BigDecimal.valueOf(0.8),
                DataSource.CITYBIKES,
                indicator,
                2026,
                "revision",
                HintReviewStatus.IN_REVIEW,
                Instant.now(),
                null
        );
    }

    private AiReviewCandidate contextualCandidate(String hintText) {
        return new AiReviewCandidate(
                1L,
                2L,
                "DE",
                "Germany",
                "Europe",
                "Western Europe",
                "Berlin",
                BigDecimal.ONE,
                null,
                null,
                hintText,
                null,
                null,
                25,
                BigDecimal.valueOf(0.8),
                DataSource.CIA_FACTBOOK,
                "CIA_VOTING_AGE_1",
                2026,
                "revision",
                HintReviewStatus.IN_REVIEW,
                Instant.now(),
                null
        );
    }
}
