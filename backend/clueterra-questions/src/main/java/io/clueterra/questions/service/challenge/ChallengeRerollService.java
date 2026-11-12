package io.clueterra.questions.service.challenge;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.AppSettingsReader;
import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.config.ChallengeProfileFactory;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.GameMode;
import io.clueterra.questions.exception.ConflictException;
import io.clueterra.questions.exception.ResourceNotFoundException;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;
import io.clueterra.questions.repository.jdbc.HintUsageLogJdbcRepository;
import io.clueterra.questions.service.hint.HintSelectorService;

@Service
@Slf4j
public class ChallengeRerollService {

    private final DailyChallengeJdbcRepository challengeRepository;
    private final CountryJdbcRepository countryRepository;
    private final HintRepository hintRepository;
    private final HintUsageLogJdbcRepository usageLogRepository;
    private final HintSelectorService hintSelectorService;
    private final ChallengeCountryPoolService countryPoolService;
    private final AppSettingsReader settingsReader;
    private final ChallengeJsonCodec jsonCodec;
    private final int hintCooldownDays;
    private final int countryCooldownDays;
    private final Clock clock;

    public ChallengeRerollService(
            DailyChallengeJdbcRepository challengeRepository,
            CountryJdbcRepository countryRepository,
            HintRepository hintRepository,
            HintUsageLogJdbcRepository usageLogRepository,
            HintSelectorService hintSelectorService,
            ChallengeCountryPoolService countryPoolService,
            AppSettingsReader settingsReader,
            ChallengeJsonCodec jsonCodec,
            AppProperties appProperties,
            Clock clock) {
        this.challengeRepository = challengeRepository;
        this.countryRepository = countryRepository;
        this.hintRepository = hintRepository;
        this.usageLogRepository = usageLogRepository;
        this.hintSelectorService = hintSelectorService;
        this.countryPoolService = countryPoolService;
        this.settingsReader = settingsReader;
        this.jsonCodec = jsonCodec;
        this.hintCooldownDays = appProperties.getHints().getReuseCooldownDays();
        this.countryCooldownDays = appProperties.getChallenge().getReuseCooldownDays();
        this.clock = clock;
    }

    @Transactional
    public DailyChallenge rerollHint(Long challengeId, int countryOrder, int hintOrder) {
        DailyChallenge challenge = loadAndValidateForHintReroll(challengeId);

        List<ChallengeHintEntry> hints = jsonCodec.readHints(challenge.getHintsJson());
        ChallengeHintEntry targetHint = hints.stream()
                .filter(h -> h.countryOrder() == countryOrder && h.hintOrder() == hintOrder)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Hint not found at countryOrder=" + countryOrder + ", hintOrder=" + hintOrder));
        Long countryId = jsonCodec.readCountries(challenge.getCountriesJson()).stream()
                .filter(c -> c.order() == countryOrder)
                .findFirst()
                .map(ChallengeCountryEntry::countryId)
                .orElseThrow(() -> new ResourceNotFoundException("Country not found at order=" + countryOrder));

        Set<Long> excludeHintIds = hints.stream().map(ChallengeHintEntry::hintId).collect(Collectors.toSet());
        excludeHintIds.addAll(usageLogRepository.findRecentlyUsedHintIds(
                countryId, GameMode.DAILY, challenge.getChallengeDate().minusDays(hintCooldownDays)));

        List<ChallengeHintEntry> countryHints = hints.stream().filter(h -> h.countryOrder() == countryOrder).toList();
        Set<String> existingCategories = countryHints.stream().map(ChallengeHintEntry::category).collect(Collectors.toSet());
        Set<String> existingTypes = countryHints.stream().map(ChallengeHintEntry::type).collect(Collectors.toSet());
        Set<String> existingSources = countryHints.stream().map(ChallengeHintEntry::source).collect(Collectors.toSet());

        Hint newHint = hintRepository.findActiveByCountryId(countryId).stream()
                .filter(h -> !excludeHintIds.contains(h.getId()))
                .map(HintCandidate::from)
                .max(Comparator.comparingDouble(candidate ->
                        scoreCandidateForReroll(candidate, existingCategories, existingTypes, existingSources)))
                .map(HintCandidate::hint)
                .orElseThrow(() -> new ConflictException("No replacement hints available for country " + countryId));

        hints.set(hints.indexOf(targetHint), ChallengeHintEntry.of(countryOrder, hintOrder, newHint));

        hintSelectorService.markHintsAsUsed(List.of(newHint));
        hintRepository.batchUnmarkUsed(List.of(targetHint.hintId()));
        usageLogRepository.deleteBatch(List.of(targetHint.hintId()), challenge.getChallengeDate(), GameMode.DAILY);
        usageLogRepository.save(newHint.getId(), countryId, challenge.getChallengeDate(), GameMode.DAILY, challenge.getId());

        challenge.setHintsJson(jsonCodec.writeHints(hints));
        challenge.setTotalHints(hints.size());
        challengeRepository.update(challenge);

        log.info("Rerolled hint at country={}, hint={} in challenge {}. New hint: {} (id={})",
                countryOrder, hintOrder, challengeId, newHint.getHintText(), newHint.getId());
        return challenge;
    }

    @Transactional
    public DailyChallenge rerollCountry(Long challengeId, int countryOrder) {
        DailyChallenge challenge = loadAndValidateForCountryReroll(challengeId);
        LocalDate date = challenge.getChallengeDate();
        ChallengeLevel level = challenge.getChallengeLevel();

        List<ChallengeCountryEntry> countries = jsonCodec.readCountries(challenge.getCountriesJson());
        List<ChallengeHintEntry> hints = jsonCodec.readHints(challenge.getHintsJson());
        ChallengeCountryEntry targetCountry = countries.stream()
                .filter(c -> c.order() == countryOrder)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Country not found at order=" + countryOrder));

        Set<Long> exclude = countries.stream().map(ChallengeCountryEntry::countryId).collect(Collectors.toSet());
        exclude.addAll(sameDateOtherLevelCountryIds(date, level));
        Set<Long> excludeWithCooldown = new HashSet<>(exclude);
        excludeWithCooldown.addAll(recentlyUsedCountryIds(date, level));

        ChallengeProfile profile = ChallengeProfileFactory.forLevel(level);
        List<Country> allCountries = countryRepository.findByIsActiveTrue();
        List<Country> candidates = countryPoolService.selectCountries(date, profile, 1, excludeWithCooldown, allCountries);
        if (candidates.isEmpty()) {
            log.warn("No replacement country outside the cooldown for challenge {}, relaxing cooldown", challengeId);
            candidates = countryPoolService.selectCountries(date, profile, 1, exclude, allCountries);
        }
        Country newCountry = candidates.stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("No replacement country available"));

        ChallengePlan plan = ChallengePlanBuilder.build(profile, 1, settingsReader.getHintsPerCountry());
        List<Hint> newHints = hintSelectorService.selectHintsForChallenge(plan, List.of(newCountry), date)
                .getOrDefault(newCountry, List.of());

        List<Long> removedHintIds = hints.stream()
                .filter(h -> h.countryOrder() == countryOrder)
                .map(ChallengeHintEntry::hintId)
                .toList();
        hints.removeIf(h -> h.countryOrder() == countryOrder);
        for (int i = 0; i < newHints.size(); i++) {
            hints.add(ChallengeHintEntry.of(countryOrder, i + 1, newHints.get(i)));
        }
        countries.set(countries.indexOf(targetCountry), ChallengeCountryEntry.of(countryOrder, newCountry));

        challenge.setCountriesJson(jsonCodec.writeCountries(countries));
        challenge.setHintsJson(jsonCodec.writeHints(hints));
        challenge.setTotalCountries(countries.size());
        challenge.setTotalHints(hints.size());
        challengeRepository.update(challenge);

        usageLogRepository.deleteBatch(removedHintIds, date, GameMode.DAILY);
        hintRepository.batchUnmarkUsed(removedHintIds);
        countryRepository.adjustTimesFeatured(targetCountry.countryId(), -1);
        countryRepository.incrementTimesFeatured(newCountry.getId());

        log.info("Rerolled country at order={} in challenge {}. New country: {} ({})",
                countryOrder, challengeId, newCountry.getNameEn(), newCountry.getIsoAlpha2());
        return challenge;
    }

    private DailyChallenge loadAndValidateForHintReroll(Long challengeId) {
        DailyChallenge challenge = loadChallengeForUpdate(challengeId);
        if (challenge.getChallengeDate().isBefore(LocalDate.now(clock))) {
            throw new ConflictException("Cannot modify past challenge " + challengeId);
        }
        return challenge;
    }

    private DailyChallenge loadAndValidateForCountryReroll(Long challengeId) {
        DailyChallenge challenge = loadChallengeForUpdate(challengeId);
        LocalDate today = LocalDate.now(clock);
        if (challenge.getChallengeDate().isBefore(today)) {
            throw new ConflictException("Cannot modify past challenge " + challengeId);
        }
        if (challenge.getChallengeDate().equals(today)) {
            throw new ConflictException("Cannot reroll countries in today's challenge " + challengeId
                    + ". Only hint rerolls are allowed for today.");
        }
        return challenge;
    }

    private DailyChallenge loadChallengeForUpdate(Long challengeId) {
        DailyChallenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge not found: " + challengeId));
        if (!Boolean.TRUE.equals(challenge.getIsActive())) {
            throw new ConflictException("Challenge " + challengeId + " is not active");
        }
        return challenge;
    }

    private Set<Long> sameDateOtherLevelCountryIds(LocalDate date, ChallengeLevel currentLevel) {
        Set<Long> ids = new HashSet<>();
        for (ChallengeLevel otherLevel : ChallengeLevel.values()) {
            if (otherLevel != currentLevel) {
                challengeRepository.findByDateAndLevel(date, otherLevel).ifPresent(c -> ids.addAll(countryIds(c)));
            }
        }
        return ids;
    }

    private Set<Long> recentlyUsedCountryIds(LocalDate date, ChallengeLevel level) {
        return challengeRepository.findByDateRangeAndLevel(date.minusDays(countryCooldownDays), date.minusDays(1), level)
                .stream()
                .flatMap(challenge -> countryIds(challenge).stream())
                .collect(Collectors.toSet());
    }

    private Set<Long> countryIds(DailyChallenge challenge) {
        return jsonCodec.readCountries(challenge.getCountriesJson()).stream()
                .map(ChallengeCountryEntry::countryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private double scoreCandidateForReroll(
            HintCandidate candidate,
            Set<String> existingCategories,
            Set<String> existingTypes,
            Set<String> existingSources) {

        Hint hint = candidate.hint();
        double score = 0;
        if (!existingCategories.contains(hint.getCategory().name())) {
            score += 3;
        }
        if (!existingTypes.contains(hint.getHintType().name())) {
            score += 3;
        }
        if (hint.getSource() != null && !existingSources.contains(hint.getSource().name())) {
            score += 2;
        }
        if (hint.getQualityScore() != null) {
            score += hint.getQualityScore().doubleValue();
        }
        if (candidate.hasImage()) {
            score += 2;
        }
        return score;
    }
}
