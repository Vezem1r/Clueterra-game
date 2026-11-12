package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.enums.DataSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class LensRegistry {

    private final List<HintLens> lenses;
    private final Set<String> dynamicPrefixes;

    public LensRegistry(List<HintLens> lensBeans) {
        List<HintLens> all = lensBeans.stream()
                .sorted(Comparator.comparing(HintLens::name))
                .toList();
        this.lenses = all;

        Set<String> dynamic = new HashSet<>();
        for (HintLens lens : all) {
            if (lens.lensType() == LensType.SPECIALIZED_DYNAMIC) {
                dynamic.addAll(lens.claimedPrefixes());
            }
        }
        this.dynamicPrefixes = Collections.unmodifiableSet(dynamic);
    }

    public List<HintLens> getAllLenses() {
        return lenses;
    }

    public boolean isClaimed(DataSource source, String indicatorCode) {
        if (indicatorCode == null) {
            return false;
        }
        return lenses.stream().anyMatch(lens -> lens.ownsIndicator(source, indicatorCode));
    }

    public boolean isDynamicIndicator(String indicatorCode) {
        if (indicatorCode == null) return false;
        return dynamicPrefixes.stream().anyMatch(indicatorCode::startsWith);
    }

    public void invalidateAllCaches() {
        lenses.forEach(HintLens::invalidateCache);
    }
}
