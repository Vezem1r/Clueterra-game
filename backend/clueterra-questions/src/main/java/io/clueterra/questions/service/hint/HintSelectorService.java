package io.clueterra.questions.service.hint;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.GameMode;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.HintUsageLogJdbcRepository;
import io.clueterra.questions.service.challenge.ChallengeHintAllocationService;
import io.clueterra.questions.service.challenge.ChallengePlan;
import io.clueterra.questions.service.challenge.HintCandidate;

@Service
@Slf4j
public class HintSelectorService {

    private final HintRepository hintRepository;
    private final HintUsageLogJdbcRepository usageLogRepository;
    private final ChallengeHintAllocationService allocationService;
    private final int cooldownDays;

    public HintSelectorService(
            HintRepository hintRepository,
            HintUsageLogJdbcRepository usageLogRepository,
            ChallengeHintAllocationService allocationService,
            AppProperties appProperties) {
        this.hintRepository = hintRepository;
        this.usageLogRepository = usageLogRepository;
        this.allocationService = allocationService;
        this.cooldownDays = appProperties.getHints().getReuseCooldownDays();
    }

    public Map<Country, List<Hint>> selectHintsForChallenge(
            ChallengePlan plan, List<Country> countries, LocalDate challengeDate) {

        Map<Country, List<HintCandidate>> candidatesByCountry = new LinkedHashMap<>();

        for (Country country : countries) {
            List<HintCandidate> candidates = loadCandidates(country, plan.hintsPerCountry(), challengeDate);
            candidatesByCountry.put(country, candidates);
        }

        Map<Country, List<Hint>> allocated = allocationService.allocateHints(plan, candidatesByCountry);

        for (Map.Entry<Country, List<Hint>> entry : allocated.entrySet()) {
            markHintsAsUsed(entry.getValue());
            List<Long> hintIds = entry.getValue().stream().map(Hint::getId).toList();
            usageLogRepository.saveBatch(
                    hintIds, entry.getKey().getId(), challengeDate, GameMode.DAILY);
            logSelectedHints(entry.getKey(), entry.getValue());
        }

        return allocated;
    }

    private List<HintCandidate> loadCandidates(Country country, int hintsPerCountry, LocalDate challengeDate) {
        List<Hint> availableHints = hintRepository.findActiveByCountryId(country.getId());
        if (availableHints.size() < hintsPerCountry * 2) {
            log.warn("Country {} has only {} active hints for a challenge needing {} per country",
                    country.getIsoAlpha2(), availableHints.size(), hintsPerCountry);
        }

        LocalDate since = challengeDate.minusDays(cooldownDays);
        Set<Long> recentlyUsedIds = new HashSet<>(
                usageLogRepository.findRecentlyUsedHintIds(country.getId(), GameMode.DAILY, since));

        List<HintCandidate> candidates = availableHints.stream()
                .filter(h -> !recentlyUsedIds.contains(h.getId()))
                .map(HintCandidate::from)
                .toList();

        if (candidates.size() < hintsPerCountry) {
            LocalDate reducedSince = challengeDate.minusDays(7);
            Set<Long> reducedUsedIds = new HashSet<>(
                    usageLogRepository.findRecentlyUsedHintIds(country.getId(), GameMode.DAILY, reducedSince));
            candidates = availableHints.stream()
                    .filter(h -> !reducedUsedIds.contains(h.getId()))
                    .map(HintCandidate::from)
                    .toList();
        }

        if (candidates.size() < hintsPerCountry) {
            log.warn("Country {} has only {} candidates after all cooldown relaxation",
                    country.getIsoAlpha2(), candidates.size());
            candidates = availableHints.stream()
                    .map(HintCandidate::from)
                    .toList();
        }

        return candidates;
    }

    public void markHintsAsUsed(List<Hint> hints) {
        if (hints.isEmpty()) return;
        List<Long> hintIds = hints.stream().map(Hint::getId).toList();
        hintRepository.batchMarkUsed(hintIds);
        log.debug("Marked {} hints as used", hints.size());
    }

    private void logSelectedHints(Country country, List<Hint> hints) {
        if (!log.isDebugEnabled()) return;

        log.debug("Selected hints for {} ({}):", country.getNameEn(), country.getIsoAlpha2());
        for (int i = 0; i < hints.size(); i++) {
            Hint h = hints.get(i);
            log.debug(
                    "  {}: [D:{}] [Q:{}] [{}] {}",
                    i + 1,
                    h.getDifficulty(),
                    String.format("%.2f", h.getQualityScore()),
                    h.getCategory(),
                    h.getHintText());
        }
    }
}
