package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.HintSaveResult;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class HintLifecycleService {

    private final HintRepository hintRepository;
    private final HintFactory hintFactory;

    @Transactional
    public SourceOwnedLifecycleResult applySourceOwnedLifecycle(
            Country country,
            List<CandidateHint> candidates,
            Set<DataSource> sourcesToClean) {

        if ((candidates == null || candidates.isEmpty()) && (sourcesToClean == null || sourcesToClean.isEmpty())) {
            return SourceOwnedLifecycleResult.empty();
        }

        List<CandidateHint> accepted = selectBestSourceOwnedCandidates(candidates);
        Map<DataSource, Set<String>> activeKeysBySource = new LinkedHashMap<>();
        List<CandidateHint> persisted = new ArrayList<>();
        int newCount = 0;
        int updatedCount = 0;

        for (CandidateHint candidate : accepted) {
            HintSaveResult saveResult = saveWithArchive(country, candidate);
            if (saveResult == null) {
                continue;
            }

            persisted.add(candidate);
            if (saveResult.isNew()) {
                newCount++;
            }
            if (saveResult.isUpdated()) {
                updatedCount++;
            }

            DataSource source = saveResult.hint().getSource();
            if (source != null) {
                activeKeysBySource.computeIfAbsent(source, ignored -> new HashSet<>())
                        .add(hintIdentity(saveResult.hint()));
            }
        }

        if (sourcesToClean != null) {
            for (DataSource source : sourcesToClean) {
                Set<String> activeKeys = activeKeysBySource.getOrDefault(source, Set.of());
                List<Long> staleIds = hintRepository.findActiveByCountryIdAndSource(country.getId(), source).stream()
                        .filter(existing -> !activeKeys.contains(hintIdentity(existing)))
                        .filter(existing -> source == DataSource.BUDGET_PROJECTS
                                || !Boolean.TRUE.equals(existing.getEditorialTextLocked())
                                || existing.getReviewRetryAfterAt() != null)
                        .map(Hint::getId)
                        .filter(Objects::nonNull)
                        .toList();

                int archived = hintRepository.archiveAndDeleteByIds(staleIds, "stale_source_regen");
                if (archived > 0) {
                    log.debug("Archived {} stale source-owned hints for {} [{}]", archived, country.getIsoAlpha2(), source);
                }
            }
        }

        return new SourceOwnedLifecycleResult(persisted, newCount, updatedCount);
    }

    public HintSaveResult saveWithArchive(Country country, CandidateHint candidate) {
        return hintFactory.fromCandidate(country, candidate)
                .map(hint -> new HintSaveResult(hint, hintRepository.saveWithArchive(hint)))
                .orElse(null);
    }

    public String hintIdentity(CandidateHint candidate) {
        Category category = candidate.category() != null ? candidate.category() : Category.GENERAL;
        HintType hintType = candidate.type() != null ? candidate.type() : HintType.ABSOLUTE_FACT;
        String source = candidate.source() != null ? candidate.source().name() : "";
        String indicator = candidate.sourceIndicator() != null ? candidate.sourceIndicator() : "";
        return source + "|" + category.name() + "|" + hintType.name() + "|" + indicator;
    }

    public String hintIdentity(Hint hint) {
        String source = hint.getSource() != null ? hint.getSource().name() : "";
        String indicator = hint.getSourceIndicator() != null ? hint.getSourceIndicator() : "";
        return source + "|" + hint.getCategory().name() + "|" + hint.getHintType().name() + "|" + indicator;
    }

    private List<CandidateHint> selectBestSourceOwnedCandidates(List<CandidateHint> candidates) {
        if (candidates == null) {
            return List.of();
        }

        Map<String, CandidateHint> bestByKey = new LinkedHashMap<>();
        for (CandidateHint candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String key = hintIdentity(candidate);
            bestByKey.merge(key, candidate, (existing, challenger) ->
                    challenger.estimatedQuality().compareTo(existing.estimatedQuality()) > 0
                            ? challenger
                            : existing);
        }
        return new ArrayList<>(bestByKey.values());
    }
}
