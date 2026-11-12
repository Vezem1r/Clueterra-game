package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationBatchPlan;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiGenerationBatchPlanner {

    private final AiProperties aiProperties;
    private final AiTokenEstimator tokenEstimator;

    public List<AiGenerationBatchPlan> planBatches(List<AiGenerationCountryGapCandidate> countries) {
        List<AiGenerationBatchPlan> batches = new ArrayList<>();
        if (countries == null || countries.isEmpty()) {
            return batches;
        }

        int maxRequests = aiProperties.getGeneration().getMaxRequestsPerRun();
        int softCap = aiProperties.getGeneration().getCountryBatchSizeSoftCap();
        int tokenBudget = Math.max(1, tokenEstimator.targetInputTokensPerRequest());

        List<AiGenerationCountryGapCandidate> currentBatch = new ArrayList<>();
        long currentTokens = tokenEstimator.baseRequestOverheadTokens();
        int stageIndex = 1;

        for (AiGenerationCountryGapCandidate country : countries) {
            long candidateTokens = tokenEstimator.estimateGenerationCountryTokens(country);
            boolean exceedsSoftCap = currentBatch.size() >= softCap;
            boolean exceedsTokenBudget = !currentBatch.isEmpty() && (currentTokens + candidateTokens) > tokenBudget;

            if (exceedsSoftCap || exceedsTokenBudget) {
                batches.add(new AiGenerationBatchPlan(stageIndex++, List.copyOf(currentBatch), currentTokens));
                currentBatch = new ArrayList<>();
                currentTokens = tokenEstimator.baseRequestOverheadTokens();
            }

            if (batches.size() >= maxRequests) {
                break;
            }

            currentBatch.add(country);
            currentTokens += candidateTokens;
        }

        if (!currentBatch.isEmpty() && batches.size() < maxRequests) {
            batches.add(new AiGenerationBatchPlan(stageIndex, List.copyOf(currentBatch), currentTokens));
        }

        return batches;
    }
}
