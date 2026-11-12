package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.model.review.AiHintReviewItemResult;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewDecision;
import io.clueterra.questions.ai.model.review.AiReviewDecisionResolution;
import io.clueterra.questions.ai.service.review.AiReviewDecisionPolicy;
import io.clueterra.questions.ai.service.review.AiReviewQualityAnalyzer;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewDecisionPolicyTest {

    private final AiReviewDecisionPolicy policy = new AiReviewDecisionPolicy(new AiReviewQualityAnalyzer());

    @Test
    void convertsFactualDisableToEscalation() {
        AiReviewDecisionResolution result = policy.resolve(candidate("Completely landlocked."), item(
                "DISABLE", "CONTRADICTED", "STRONG", "BROKEN", "OK", "FACT_NEEDS_VERIFICATION",
                null, null, 1.0
        ));

        assertThat(result.decision()).isEqualTo(AiReviewDecision.ESCALATE);
        assertThat(result.confidence()).isEqualTo(0.99);
        assertThat(result.policyReason()).contains("factual defect");
    }

    @Test
    void preservesDisableOnlyForExplicitSafeReason() {
        AiReviewDecisionResolution result = policy.resolve(candidate("The answer is Germany."), item(
                "DISABLE", "CONSISTENT", "STRONG", "BROKEN", "OK", "ANSWER_LEAK",
                null, null, 0.94
        ));

        assertThat(result.decision()).isEqualTo(AiReviewDecision.DISABLE);
    }

    @Test
    void buildsDifficultyOnlyEditFromOriginalText() {
        AiReviewCandidate candidate = candidate("A niche local bike operator runs this network.");
        AiReviewDecisionResolution result = policy.resolve(candidate, item(
                "EDIT_TEXT", "CONSISTENT", "STRONG", "STRONG", "TOO_EASY", "DIFFICULTY",
                null, 62, 0.87
        ));

        assertThat(result.decision()).isEqualTo(AiReviewDecision.EDIT_TEXT);
        assertThat(result.proposedText()).isEqualTo(candidate.hintText());
        assertThat(result.proposedDifficulty()).isEqualTo(62);
    }

    @Test
    void rejectsNoOpTextEdit() {
        AiReviewCandidate candidate = candidate("Already polished text.");
        AiReviewDecisionResolution result = policy.resolve(candidate, item(
                "EDIT_TEXT", "CONSISTENT", "STRONG", "IMPROVABLE", "OK", "TEXT_QUALITY",
                "Already polished text.", null, 0.81
        ));

        assertThat(result.decision()).isEqualTo(AiReviewDecision.ERROR);
        assertThat(result.policyReason()).contains("does not change");
    }

    @Test
    void rejectsKeepThatConflictsWithQualityAssessment() {
        AiReviewDecisionResolution result = policy.resolve(candidate("Generic unfinished wording"), item(
                "KEEP", "CONSISTENT", "WEAK", "IMPROVABLE", "OK", "TEXT_QUALITY",
                null, null, 0.80
        ));

        assertThat(result.decision()).isEqualTo(AiReviewDecision.ERROR);
        assertThat(result.policyReason()).contains("quality gate");
    }

    @Test
    void preservesKeepOnlyWhenMandatoryAssessmentPasses() {
        AiReviewDecisionResolution result = policy.resolve(
                candidate("A specific named anchor makes this clue useful."),
                item(
                        "KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                        null, null, 0.88
                )
        );

        assertThat(result.decision()).isEqualTo(AiReviewDecision.KEEP);
        assertThat(result.policyReason()).isNull();
    }

    @Test
    void keepsCurrentLeaderAndSchedulesRepeatReview() {
        AiReviewCandidate leader = candidate("President Bajram Begaj currently holds the office.");
        AiHintReviewItemResult verdict = item(
                "KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                null, null, 0.88);

        assertThat(policy.resolve(leader, verdict).decision()).isEqualTo(AiReviewDecision.KEEP);
        assertThat(policy.retryAfterDays(leader, verdict, AiReviewDecision.KEEP)).isEqualTo(90);
    }

    @Test
    void doesNotSchedulePermanentFactOrEscalatedLeader() {
        AiReviewCandidate historical = candidate("A specific historical event happened in 1815.");
        AiHintReviewItemResult verdict = item(
                "KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                null, null, 0.88);

        assertThat(policy.retryAfterDays(historical, verdict, AiReviewDecision.KEEP)).isNull();
        assertThat(policy.retryAfterDays(candidate("Current president is disputed."), verdict,
                AiReviewDecision.ESCALATE)).isNull();
    }

    @Test
    void honorsModelRequestedRetryForChangingFactWithoutKeyword() {
        AiHintReviewItemResult verdict = new AiHintReviewItemResult(
                1L, "KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                0.88, "Changing policy", "Provided country source", null, null,
                null, null, 180);

        assertThat(policy.retryAfterDays(candidate("This policy remains in force."), verdict,
                AiReviewDecision.KEEP)).isEqualTo(180);
    }

    @Test
    void trustedLeadershipWithoutSourceYearIsKeptForNinetyDayReview() {
        AiReviewCandidate leader = candidate("At the top sits President Example.",
                "CIA_CHIEF_OF_STATE_1", null);
        AiHintReviewItemResult verdict = item(
                "KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                null, null, 0.88);

        assertThat(policy.resolve(leader, verdict).decision()).isEqualTo(AiReviewDecision.KEEP);
        assertThat(policy.retryAfterDays(leader, verdict, AiReviewDecision.KEEP)).isEqualTo(90);
    }

    @Test
    void undatedUntrustedLeadershipStillEscalates() {
        AiReviewDecisionResolution verdict = policy.resolve(
                candidate("At the top sits President Example.", "OTHER_SOURCE", null),
                item("KEEP", "CONSISTENT", "STRONG", "STRONG", "OK", "KEEP_STRONG",
                        null, null, 0.88));

        assertThat(verdict.decision()).isEqualTo(AiReviewDecision.ESCALATE);
    }

    private AiHintReviewItemResult item(
            String decision,
            String factStatus,
            String anchorStatus,
            String textStatus,
            String difficultyStatus,
            String reasonCode,
            String proposedText,
            Integer proposedDifficulty,
            Double confidence) {
        return new AiHintReviewItemResult(
                1L,
                decision,
                factStatus,
                anchorStatus,
                textStatus,
                difficultyStatus,
                reasonCode,
                confidence,
                "reason",
                "difficulty: ok",
                proposedText,
                proposedDifficulty,
                null,
                decision.equals("DISABLE") ? "unsafe hint" : null
        );
    }

    private AiReviewCandidate candidate(String hintText) {
        return candidate(hintText, "CIA_TEST", 2026);
    }

    private AiReviewCandidate candidate(String hintText, String indicator, Integer sourceYear) {
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
                25,
                BigDecimal.valueOf(0.8),
                DataSource.CIA_FACTBOOK,
                indicator,
                sourceYear,
                "revision",
                HintReviewStatus.IN_REVIEW,
                Instant.now(),
                null
        );
    }
}
