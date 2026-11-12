package io.clueterra.questions.service.challenge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.entity.enums.HintBand;

public final class ChallengePlanBuilder {

    private ChallengePlanBuilder() {
    }

    public static ChallengePlan build(
            ChallengeProfile profile, int countriesCount, int hintsPerCountry) {

        int totalSlots = countriesCount * hintsPerCountry;
        Map<HintBand, Integer> bandQuotas = computeBandQuotas(profile, totalSlots);
        int requiredImageHints = profile.imageQuotaRule().compute(totalSlots);
        int[] slotDifficultyTargets = profile.difficultyCurve().computeTargets(hintsPerCountry);
        HintBand[][] slotBandTargets = computeSlotBandTargets(
                countriesCount,
                hintsPerCountry,
                bandQuotas,
                slotDifficultyTargets
        );

        return new ChallengePlan(
                profile,
                countriesCount,
                hintsPerCountry,
                totalSlots,
                bandQuotas,
                requiredImageHints,
                slotDifficultyTargets,
                slotBandTargets,
                profile.minDifficulty());
    }

    private static Map<HintBand, Integer> computeBandQuotas(
            ChallengeProfile profile, int totalSlots) {

        Map<HintBand, Double> ratios = profile.hintBandRatios();
        Map<HintBand, Integer> quotas = new EnumMap<>(HintBand.class);

        int assigned = 0;
        Map<HintBand, Double> remainders = new EnumMap<>(HintBand.class);

        for (Map.Entry<HintBand, Double> entry : ratios.entrySet()) {
            double exact = entry.getValue() * totalSlots;
            int floored = (int) Math.floor(exact);
            quotas.put(entry.getKey(), floored);
            remainders.put(entry.getKey(), exact - floored);
            assigned += floored;
        }

        int leftover = totalSlots - assigned;
        remainders.entrySet().stream()
                .sorted(Map.Entry.<HintBand, Double>comparingByValue().reversed())
                .limit(leftover)
                .forEach(e -> quotas.merge(e.getKey(), 1, Integer::sum));

        return Map.copyOf(quotas);
    }

    private static HintBand[][] computeSlotBandTargets(
            int countriesCount,
            int hintsPerCountry,
            Map<HintBand, Integer> bandQuotas,
            int[] slotDifficultyTargets) {

        HintBand[][] targets = new HintBand[countriesCount][hintsPerCountry];
        if (countriesCount == 0 || hintsPerCountry == 0) {
            return targets;
        }

        List<SlotRef> slots = new ArrayList<>(countriesCount * hintsPerCountry);
        for (int slotIndex = 0; slotIndex < hintsPerCountry; slotIndex++) {
            int targetDifficulty = slotDifficultyTargets != null && slotIndex < slotDifficultyTargets.length
                    ? slotDifficultyTargets[slotIndex]
                    : 50;
            for (int countryIndex = 0; countryIndex < countriesCount; countryIndex++) {
                slots.add(new SlotRef(countryIndex, slotIndex, targetDifficulty));
            }
        }

        slots.sort(Comparator.comparingInt(SlotRef::targetDifficulty).reversed()
                .thenComparingInt(SlotRef::slotIndex)
                .thenComparingInt(SlotRef::countryIndex));

        int cursor = 0;
        cursor = assignBand(targets, slots, cursor, bandQuotas.getOrDefault(HintBand.HARD_HINT, 0), HintBand.HARD_HINT);
        cursor = assignBand(targets, slots, cursor, bandQuotas.getOrDefault(HintBand.MEDIUM_HINT, 0), HintBand.MEDIUM_HINT);
        cursor = assignBand(targets, slots, cursor, bandQuotas.getOrDefault(HintBand.EASY_HINT, 0), HintBand.EASY_HINT);

        for (SlotRef slot : slots) {
            if (targets[slot.countryIndex()][slot.slotIndex()] == null) {
                targets[slot.countryIndex()][slot.slotIndex()] = HintBand.fromDifficulty(slot.targetDifficulty());
            }
        }

        return targets;
    }

    private static int assignBand(
            HintBand[][] targets,
            List<SlotRef> slots,
            int startIndex,
            int count,
            HintBand band) {

        int assigned = 0;
        int cursor = startIndex;
        while (cursor < slots.size() && assigned < count) {
            SlotRef slot = slots.get(cursor++);
            if (targets[slot.countryIndex()][slot.slotIndex()] == null) {
                targets[slot.countryIndex()][slot.slotIndex()] = band;
                assigned++;
            }
        }
        return cursor;
    }
}
