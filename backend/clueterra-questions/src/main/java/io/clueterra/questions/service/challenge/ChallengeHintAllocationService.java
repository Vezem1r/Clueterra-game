package io.clueterra.questions.service.challenge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.HintType;

@Service
@Slf4j
public class ChallengeHintAllocationService {

    private static final int MAX_HINTS_PER_DATASOURCE = 2;

    public Map<Country, List<Hint>> allocateHints(
            ChallengePlan plan, Map<Country, List<HintCandidate>> candidatesByCountry) {

        Map<HintBand, Integer> remainingBandQuota = new EnumMap<>(plan.bandQuotas());
        int imageQuotaRemaining = plan.requiredImageHints();
        Set<String> globalTextFingerprints = new HashSet<>();

        Map<Country, List<Hint>> result = new LinkedHashMap<>();

        int countryIndex = 0;
        for (Map.Entry<Country, List<HintCandidate>> entry : candidatesByCountry.entrySet()) {
            Country country = entry.getKey();
            List<HintCandidate> candidates = entry.getValue();

            List<Hint> countryHints =
                    selectForCountry(
                            candidates,
                            plan.hintsPerCountry(),
                            plan.slotDifficultyTargets(),
                            plan.slotBandTargets() != null && countryIndex < plan.slotBandTargets().length
                                    ? plan.slotBandTargets()[countryIndex]
                                    : null,
                            remainingBandQuota,
                            imageQuotaRemaining,
                            globalTextFingerprints,
                            plan.minDifficulty());

            imageQuotaRemaining -=
                    (int) countryHints.stream()
                            .filter(h -> h.getImageUrl() != null && !h.getImageUrl().isBlank())
                            .count();

            countryHints.sort(Comparator.comparing(
                    Hint::getDifficulty,
                    Comparator.nullsLast(Integer::compareTo)
            ).reversed());
            result.put(country, countryHints);
            countryIndex++;
        }

        return result;
    }

    private List<Hint> selectForCountry(
            List<HintCandidate> candidates,
            int hintsNeeded,
            int[] slotTargets,
            HintBand[] slotBandTargets,
            Map<HintBand, Integer> remainingBandQuota,
            int imageQuotaRemaining,
            Set<String> globalTextFingerprints,
            int minDifficulty) {

        List<Hint> selected = new ArrayList<>();
        Set<String> usedIndicators = new HashSet<>();
        Set<String> usedFamilies = new HashSet<>();
        Set<Category> usedCategories = new HashSet<>();
        Set<HintType> usedTypes = new HashSet<>();
        Set<String> usedSources = new HashSet<>();
        Map<DataSource, Integer> dataSourceCount = new HashMap<>();
        Map<HintType, Integer> typeCount = new HashMap<>();

        List<HintCandidate> remaining = new ArrayList<>(candidates);

        for (int i = 0; i < hintsNeeded && !remaining.isEmpty(); i++) {
            int targetDifficulty = (slotTargets != null && i < slotTargets.length)
                    ? slotTargets[i] : 50;
            HintBand targetBand = slotBandTargets != null && i < slotBandTargets.length && slotBandTargets[i] != null
                    ? slotBandTargets[i]
                    : HintBand.fromDifficulty(targetDifficulty);

            List<HintCandidate> pool = findPool(
                    remaining,
                    targetBand,
                    usedIndicators,
                    usedFamilies,
                    globalTextFingerprints,
                    dataSourceCount
            );

            if (pool.isEmpty()) continue;

            final int slotIndex = i;
            final int slotTarget = targetDifficulty;
            final int currentImageQuota = imageQuotaRemaining - (int) selected.stream()
                    .filter(h -> h.getImageUrl() != null && !h.getImageUrl().isBlank()).count();

            HintCandidate best = pool.stream()
                    .max(Comparator.comparingDouble(c -> scoreCandidate(
                            c, slotTarget, targetBand, slotIndex, hintsNeeded,
                            remainingBandQuota, currentImageQuota,
                            usedCategories, usedTypes, usedSources,
                            dataSourceCount, typeCount, minDifficulty)))
                    .orElse(null);

            selected.add(best.hint());
            remaining.remove(best);

            if (best.hint().getSourceIndicator() != null) {
                usedIndicators.add(best.hint().getSourceIndicator());
            }
            if (!best.sourceFamily().isEmpty()) {
                usedFamilies.add(best.sourceFamily());
            }
            if (!best.textFingerprint().isEmpty()) {
                globalTextFingerprints.add(best.textFingerprint());
            }
            usedCategories.add(best.hint().getCategory());
            usedTypes.add(best.hint().getHintType());
            typeCount.merge(best.hint().getHintType(), 1, Integer::sum);
            if (best.hint().getSource() != null) {
                usedSources.add(best.hint().getSource().name());
                dataSourceCount.merge(best.hint().getSource(), 1, Integer::sum);
            }

            Integer bandCount = remainingBandQuota.get(best.band());
            if (bandCount != null && bandCount > 0) {
                remainingBandQuota.put(best.band(), bandCount - 1);
            }
        }

        if (selected.size() < hintsNeeded && !remaining.isEmpty()) {
            List<HintCandidate> backfillPool = remaining.stream()
                    .filter(c -> {
                        String ind = c.hint().getSourceIndicator();
                        return ind == null || !usedIndicators.contains(ind);
                    })
                    .toList();
            for (HintCandidate c : backfillPool) {
                if (selected.size() >= hintsNeeded) break;
                selected.add(c.hint());
                if (c.hint().getSourceIndicator() != null) {
                    usedIndicators.add(c.hint().getSourceIndicator());
                }
            }
        }

        if (selected.size() < hintsNeeded) {
            log.warn("Allocated only {}/{} hints from {} candidates; the challenge will have fewer hints for this country",
                    selected.size(), hintsNeeded, candidates.size());
        }
        return selected;
    }

    private List<HintCandidate> findPool(
            List<HintCandidate> remaining,
            HintBand targetBand,
            Set<String> usedIndicators,
            Set<String> usedFamilies,
            Set<String> globalTextFingerprints,
            Map<DataSource, Integer> dataSourceCount) {

        List<EligibilityRules> attempts = List.of(
                new EligibilityRules(targetBand, true, true, true),
                new EligibilityRules(null, true, true, true),
                new EligibilityRules(targetBand, true, true, false),
                new EligibilityRules(null, true, true, false),
                new EligibilityRules(targetBand, true, false, false),
                new EligibilityRules(null, true, false, false),
                new EligibilityRules(targetBand, false, false, false),
                new EligibilityRules(null, false, false, false)
        );

        for (EligibilityRules rules : attempts) {
            List<HintCandidate> eligible = filterEligible(
                    remaining,
                    rules,
                    usedIndicators,
                    usedFamilies,
                    globalTextFingerprints,
                    dataSourceCount
            );
            if (!eligible.isEmpty()) {
                return eligible;
            }
        }

        return List.of();
    }

    private List<HintCandidate> filterEligible(
            List<HintCandidate> remaining,
            EligibilityRules rules,
            Set<String> usedIndicators,
            Set<String> usedFamilies,
            Set<String> globalTextFingerprints,
            Map<DataSource, Integer> dataSourceCount) {

        return remaining.stream()
                .filter(c -> {
                    String indicator = c.hint().getSourceIndicator();
                    return indicator == null || !usedIndicators.contains(indicator);
                })
                .filter(c -> rules.targetBand() == null || c.band() == rules.targetBand())
                .filter(c -> !rules.enforceFamily() || c.sourceFamily().isEmpty() || !usedFamilies.contains(c.sourceFamily()))
                .filter(c -> !rules.enforceTextFingerprint()
                        || c.textFingerprint().isEmpty()
                        || !globalTextFingerprints.contains(c.textFingerprint()))
                .filter(c -> {
                    if (!rules.enforceDataSourceCap()) {
                        return true;
                    }
                    DataSource ds = c.hint().getSource();
                    return ds == null
                            || dataSourceCount.getOrDefault(ds, 0) < MAX_HINTS_PER_DATASOURCE;
                })
                .toList();
    }

    private double scoreCandidate(
            HintCandidate candidate,
            int targetDifficulty,
            HintBand targetBand,
            int slotIndex,
            int totalSlots,
            Map<HintBand, Integer> remainingBandQuota,
            int imageQuotaRemaining,
            Set<Category> usedCategories,
            Set<HintType> usedTypes,
            Set<String> usedSources,
            Map<DataSource, Integer> dataSourceCount,
            Map<HintType, Integer> typeCount,
            int minDifficulty) {

        double score = 0;

        int difficulty = candidate.hint().getDifficulty() != null
                ? candidate.hint().getDifficulty() : 50;
        int deviation = Math.abs(difficulty - targetDifficulty);
        score += Math.max(0, 100.0 - deviation * 1.5);

        int bandDistance = Math.abs(candidate.band().ordinal() - targetBand.ordinal());
        score += switch (bandDistance) {
            case 0 -> 8.0;
            case 1 -> 2.0;
            default -> -6.0;
        };

        Integer bandRemaining = remainingBandQuota.get(candidate.band());
        if (bandRemaining != null && bandRemaining > 0) {
            score += 4.0;
        } else {
            score -= 2.0;
        }

        if (!usedCategories.contains(candidate.hint().getCategory())) {
            score += 6.0;
        }

        if (!usedTypes.contains(candidate.hint().getHintType())) {
            score += 5.0;
        }

        if (candidate.hint().getSource() != null
                && !usedSources.contains(candidate.hint().getSource().name())) {
            score += 4.0;
        }

        if (candidate.hasImage() && imageQuotaRemaining > 0) {
            score += 3.0;
        }

        BigDecimal quality = candidate.hint().getQualityScore();
        if (quality != null) {
            double q = quality.doubleValue();
            double normalizedQuality = q <= 1.0 ? q : q / 100.0;
            score += normalizedQuality * 5.0;
        }

        int timesUsed = candidate.hint().getTimesUsed() != null
                ? candidate.hint().getTimesUsed() : 0;
        score += 2.0 / (1.0 + timesUsed);

        if (minDifficulty > 0 && difficulty < minDifficulty) {
            score -= (minDifficulty - difficulty) * 3.0;
        }

        if (candidate.hint().getHintType() == HintType.PANORAMA) {
            int panoramaCount = typeCount.getOrDefault(HintType.PANORAMA, 0);
            if (panoramaCount >= 1) {
                score -= 50.0;
            }
        }

        DataSource ds = candidate.hint().getSource();
        if (ds != null) {
            int dsCount = dataSourceCount.getOrDefault(ds, 0);
            if (dsCount >= MAX_HINTS_PER_DATASOURCE) {
                score -= 15.0 * (dsCount - MAX_HINTS_PER_DATASOURCE + 1);
            }
        }

        Integer ambiguity = candidate.hint().getAmbiguityCount();
        if (ambiguity != null && ambiguity > 0 && totalSlots > 1) {
            double ambiguityWeight = 0.05 + 0.15 * ((double) slotIndex / (totalSlots - 1));
            score -= ambiguity * ambiguityWeight;
        }

        return score;
    }

}
