package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.review.AiReviewBatchPlan;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiReviewBatchPlanner {

    private final AiProperties aiProperties;
    private final AiTokenEstimator tokenEstimator;

    public List<AiReviewBatchPlan> planBatches(List<AiReviewCandidate> candidates) {
        return planBatches(candidates, aiProperties.getReview().getMaxRequestsPerRun());
    }

    public List<AiReviewBatchPlan> planBatches(List<AiReviewCandidate> candidates, int maxRequests) {
        List<AiReviewBatchPlan> batches = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return batches;
        }

        int softCap = aiProperties.getReview().getDefaultBatchSizeSoftCap();
        int tokenBudget = Math.max(1, tokenEstimator.reviewTargetInputTokensPerRequest());

        List<AiReviewCandidate> currentBatch = new ArrayList<>();
        long currentTokens = tokenEstimator.reviewBaseRequestOverheadTokens();
        int currentImages = 0;
        int stageIndex = 1;

        for (AiReviewCandidate candidate : candidates) {
            long candidateTokens = tokenEstimator.estimateReviewCandidateTokens(candidate);
            boolean exceedsSoftCap = currentBatch.size() >= softCap;
            boolean exceedsTokenBudget = !currentBatch.isEmpty() && (currentTokens + candidateTokens) > tokenBudget;

            if (exceedsSoftCap || exceedsTokenBudget) {
                batches.add(new AiReviewBatchPlan(stageIndex++, List.copyOf(currentBatch), currentTokens, currentImages));
                currentBatch = new ArrayList<>();
                currentTokens = tokenEstimator.reviewBaseRequestOverheadTokens();
                currentImages = 0;
            }

            if (batches.size() >= maxRequests) {
                break;
            }

            currentBatch.add(candidate);
            currentTokens += candidateTokens;
            if (candidate.hasImage()) {
                currentImages++;
            }
        }

        if (!currentBatch.isEmpty() && batches.size() < maxRequests) {
            batches.add(new AiReviewBatchPlan(stageIndex, List.copyOf(currentBatch), currentTokens, currentImages));
        }

        return batches;
    }
}
