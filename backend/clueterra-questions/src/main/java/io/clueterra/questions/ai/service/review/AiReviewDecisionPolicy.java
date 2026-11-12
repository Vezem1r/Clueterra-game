package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.model.review.AiHintReviewItemResult;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewDecision;
import io.clueterra.questions.ai.model.review.AiReviewDecisionResolution;
import io.clueterra.questions.entity.enums.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiReviewDecisionPolicy {

    private static final Set<AiReviewDecision> UNAVAILABLE_DECISIONS =
            Set.of(AiReviewDecision.REPLACE_IMAGE, AiReviewDecision.EDIT_TEXT_AND_IMAGE);
    private static final Set<String> ALLOWED_DISABLE_REASONS = Set.of(
            "ANSWER_LEAK",
            "EMPTY_OR_NONSENSICAL",
            "EXPLICIT_DUPLICATE"
    );
    private static final Set<String> UNSAFE_FACT_STATUSES = Set.of("SUSPECT", "CONTRADICTED", "BROKEN");
    private static final Set<String> WEAK_ANCHOR_STATUSES = Set.of("WEAK", "MISSING");
    private static final Set<String> IMPROVABLE_TEXT_STATUSES = Set.of("IMPROVABLE", "BROKEN");
    private static final Set<String> INCORRECT_DIFFICULTY_STATUSES = Set.of("TOO_EASY", "TOO_HARD");
    private static final Pattern CURRENT_FACT = Pattern.compile("(?i)\\b(current|currently|incumbent|sitting|today|now)\\b");

    private final AiReviewQualityAnalyzer qualityAnalyzer;

    public AiReviewDecisionPolicy(AiReviewQualityAnalyzer qualityAnalyzer) {
        this.qualityAnalyzer = qualityAnalyzer;
    }

    public AiReviewDecisionResolution resolve(
            AiReviewCandidate candidate,
            AiHintReviewItemResult result) {
        if (result == null) {
            return resolution(AiReviewDecision.ERROR, null, null, null, "Missing model result");
        }

        AiReviewDecision decision = parseDecision(result.decision());
        String proposedText = StringUtils.trimToNull(result.proposedText());
        Integer proposedDifficulty = result.proposedDifficulty();
        Double confidence = normalizeConfidence(result.confidence());

        if (UNAVAILABLE_DECISIONS.contains(decision)) {
            return resolution(AiReviewDecision.ESCALATE, null, null, confidence,
                    "Unsupported image decision requires manual review");
        }

        if (UNSAFE_FACT_STATUSES.contains(normalize(result.factStatus()))
                && decision != AiReviewDecision.ESCALATE
                && decision != AiReviewDecision.ERROR) {
            return resolution(AiReviewDecision.ESCALATE, null, null, confidence,
                    "Suspected factual defect cannot be auto-edited or disabled");
        }

        if (decision == AiReviewDecision.DISABLE
                && !ALLOWED_DISABLE_REASONS.contains(normalize(result.reasonCode()))) {
            return resolution(AiReviewDecision.ESCALATE, null, null, confidence,
                    "Disable reason is not permitted by review policy");
        }

        if (decision == AiReviewDecision.EDIT_TEXT) {
            return resolveTextEdit(candidate, proposedText, proposedDifficulty, confidence, null);
        }

        if (decision == AiReviewDecision.KEEP && !isValidKeep(candidate, result)) {
            if (qualityAnalyzer.analyze(candidate).contains("TIME_SENSITIVE_WITHOUT_SOURCE_YEAR")
                    && !isTrustedLeadershipSource(candidate)) {
                return resolution(AiReviewDecision.ESCALATE, null, null, confidence,
                        "Time-sensitive claim has no dated, trusted source");
            }
            if (proposedText != null || proposedDifficulty != null) {
                return resolveTextEdit(
                        candidate,
                        proposedText,
                        proposedDifficulty,
                        confidence,
                        "Model assessment requires an edit"
                );
            }
            return resolution(AiReviewDecision.ERROR, null, null, confidence,
                    "KEEP did not pass the mandatory quality gate");
        }

        return resolution(decision, null, null, confidence, null);
    }

    public Integer retryAfterDays(AiReviewCandidate candidate, AiHintReviewItemResult result,
                                  AiReviewDecision decision) {
        if (result == null || decision == AiReviewDecision.ERROR || decision == AiReviewDecision.DISABLE
                || decision == AiReviewDecision.ESCALATE) {
            return null;
        }
        boolean temporal = qualityAnalyzer.analyze(candidate).contains("TIME_SENSITIVE_WITHOUT_SOURCE_YEAR")
                || CURRENT_FACT.matcher(Objects.toString(candidate.hintText(), "")).find()
                || isTrustedLeadershipSource(candidate)
                || result.retryAfterDays() != null;
        if (!temporal) {
            return null;
        }
        Integer requested = result.retryAfterDays();
        return requested == null ? 90 : Math.clamp(requested, 30, 365);
    }

    private AiReviewDecisionResolution resolveTextEdit(
            AiReviewCandidate candidate,
            String proposedText,
            Integer proposedDifficulty,
            Double confidence,
            String policyReason) {
        String resolvedText = proposedText;
        if (resolvedText == null && proposedDifficulty != null) {
            resolvedText = candidate.hintText();
        }
        if (resolvedText == null) {
            return resolution(AiReviewDecision.ERROR, null, null, confidence,
                    "Text edit is missing proposed text");
        }
        boolean textChanged = !resolvedText.equals(StringUtils.trimToEmpty(candidate.hintText()));
        boolean difficultyChanged = proposedDifficulty != null
                && !proposedDifficulty.equals(candidate.difficulty());
        if (!textChanged && !difficultyChanged) {
            return resolution(AiReviewDecision.ERROR, null, null, confidence,
                    "Text edit does not change text or difficulty");
        }
        return resolution(
                AiReviewDecision.EDIT_TEXT,
                resolvedText,
                proposedDifficulty,
                confidence,
                policyReason
        );
    }

    private boolean hasUnresolvedImprovement(AiHintReviewItemResult result) {
        return IMPROVABLE_TEXT_STATUSES.contains(normalize(result.textStatus()))
                || WEAK_ANCHOR_STATUSES.contains(normalize(result.anchorStatus()))
                || INCORRECT_DIFFICULTY_STATUSES.contains(normalize(result.difficultyStatus()))
                || "TEXT_QUALITY".equals(normalize(result.reasonCode()))
                || "DIFFICULTY".equals(normalize(result.reasonCode()));
    }

    private boolean isValidKeep(AiReviewCandidate candidate, AiHintReviewItemResult result) {
        boolean assessmentPassed = "CONSISTENT".equals(normalize(result.factStatus()))
                && "STRONG".equals(normalize(result.anchorStatus()))
                && "STRONG".equals(normalize(result.textStatus()))
                && "OK".equals(normalize(result.difficultyStatus()))
                && "KEEP_STRONG".equals(normalize(result.reasonCode()))
                && !hasUnresolvedImprovement(result);
        boolean hasBlockingServerSignal = qualityAnalyzer.analyze(candidate).stream()
                .anyMatch(signal -> !signal.startsWith("POSSIBLY_")
                        && !(signal.equals("TIME_SENSITIVE_WITHOUT_SOURCE_YEAR")
                        && isTrustedLeadershipSource(candidate)));
        return assessmentPassed && !hasBlockingServerSignal;
    }

    private boolean isTrustedLeadershipSource(AiReviewCandidate candidate) {
        return candidate.source() == DataSource.CIA_FACTBOOK
                && Objects.toString(candidate.sourceIndicator(), "")
                .matches("(?i).*CIA_(CHIEF_OF_STATE|HEAD_OF_GOVERNMENT).*");
    }

    private AiReviewDecision parseDecision(String value) {
        try {
            return AiReviewDecision.valueOf(normalize(value));
        } catch (IllegalArgumentException ex) {
            return AiReviewDecision.ERROR;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return null;
        }
        return Math.clamp(confidence, 0.0, 0.99);
    }

    private AiReviewDecisionResolution resolution(
            AiReviewDecision decision,
            String proposedText,
            Integer proposedDifficulty,
            Double confidence,
            String policyReason) {
        return new AiReviewDecisionResolution(
                decision,
                proposedText,
                proposedDifficulty,
                confidence,
                policyReason
        );
    }
}
