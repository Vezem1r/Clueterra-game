package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.service.hint.CountryNameMatcher;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AiGenerationProposalScoringService {

    private static final Set<String> BORING_CATEGORIES = Set.of("EDUCATION", "HEALTH");

    private static final int EXPERT_DIFFICULTY_THRESHOLD = 80;
    private static final int HARD_DIFFICULTY_THRESHOLD = 65;
    private static final double EXPERT_AI_WEIGHT = 0.90;
    private static final double HARD_AI_WEIGHT = 0.60;
    private static final double DEFAULT_AI_WEIGHT = 0.40;

    private final AiGenerationHintHeuristics heuristics;

    public AiGenerationProposalScoringService(AiGenerationHintHeuristics heuristics) {
        this.heuristics = heuristics;
    }

    public int recalibrateDifficulty(
            Country country,
            String category,
            String hintType,
            String hintText,
            Integer proposedDifficulty) {
        int countryFloor = heuristics.countryDifficultyFloor(country);
        int themeTarget = countryFloor + heuristics.themeDifficultyDelta(category, hintType, hintText);
        int base = proposedDifficulty != null ? proposedDifficulty : themeTarget;
        double aiWeight;
        if (base >= EXPERT_DIFFICULTY_THRESHOLD) {
            aiWeight = EXPERT_AI_WEIGHT;
        } else if (base >= HARD_DIFFICULTY_THRESHOLD) {
            aiWeight = HARD_AI_WEIGHT;
        } else {
            aiWeight = DEFAULT_AI_WEIGHT;
        }
        int blended = (int) Math.round((base * aiWeight) + (themeTarget * (1.0 - aiWeight)));
        return Math.clamp(blended, countryFloor, 100);
    }

    public double recalibrateQuality(
            String hintText,
            String evidenceSummary,
            String duplicateCheckSummary,
            Double proposedQuality) {
        return recalibrateQuality(hintText, evidenceSummary, duplicateCheckSummary, proposedQuality, null);
    }

    public double recalibrateQuality(
            String hintText,
            String evidenceSummary,
            String duplicateCheckSummary,
            Double proposedQuality,
            String category) {
        double quality = Math.min(proposedQuality != null ? proposedQuality : 72.0, 88.0);
        boolean lowSignal = heuristics.isLowSignalHint(hintText);
        boolean formulaic = heuristics.isFormulaicHint(hintText);
        boolean numericHeavy = heuristics.isNumericHeavyHint(hintText);
        boolean bareStatistic = heuristics.isBareStatisticClue(hintText);
        boolean genericTheme = heuristics.isGenericThemeHint(hintText);
        boolean interesting = heuristics.isInterestingClue(hintText);
        boolean hasEvidence = evidenceSummary != null && !evidenceSummary.isBlank();
        boolean specificEvidence = hasEvidence
                && evidenceSummary.trim().length() >= 24
                && heuristics.extractKeywords(evidenceSummary).size() >= 3;
        String duplicateSummary = duplicateCheckSummary != null ? duplicateCheckSummary.toLowerCase() : "";
        boolean validatedSearch = duplicateSummary.contains("search")
                || duplicateSummary.contains("keyword")
                || duplicateSummary.contains("checked")
                || duplicateSummary.contains("looked up");
        boolean conflictFlagged = duplicateSummary.matches(".*(exact duplicate|near duplicate|too close|rewrote|replaced|same fact family|high conflict risk).*");

        if (!hasEvidence) {
            quality -= 20;
        }
        if (specificEvidence) {
            quality += 6;
        }
        if (lowSignal) {
            quality -= 30;
        }
        if (formulaic) {
            quality -= 18;
        }
        if (numericHeavy || bareStatistic) {
            quality -= 18;
        }
        if (genericTheme) {
            quality -= 18;
        }
        if (conflictFlagged) {
            quality -= 20;
        }
        if (validatedSearch) {
            quality += 3;
        }
        if (interesting) {
            quality += 6;
        }

        int length = hintText != null ? hintText.trim().length() : 0;
        if (length >= 55 && length <= 170) {
            quality += 3;
        } else {
            quality -= 6;
        }

        if (category != null && BORING_CATEGORIES.contains(category) && !interesting) {
            quality -= 12;
        }

        return Math.clamp(quality, 0.0, 93.0);
    }

    public boolean containsCountryName(String hintText, Country country) {
        return CountryNameMatcher.revealsCountry(hintText, country);
    }
}
