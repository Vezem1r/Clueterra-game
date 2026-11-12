package io.clueterra.questions.service.challenge;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.AppSettingsReader;
import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.config.ChallengeProfileFactory;
import io.clueterra.questions.config.ChallengeProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.Difficulty;
import io.clueterra.questions.entity.enums.VisibilityTier;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.hint.HintSelectorService;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;

@Service
@Slf4j
public class DailyChallengeGeneratorService {

    private final CountryJdbcRepository countryRepository;
    private final DailyChallengeJdbcRepository challengeRepository;
    private final HintSelectorService hintSelectorService;
    private final ChallengeCountryPoolService countryPoolService;
    private final CountryVisibilityProfileService visibilityProfileService;
    private final ChallengeProperties config;
    private final AppSettingsReader settingsReader;
    private final ConsoleLogWriter console;
    private final ChallengeJsonCodec jsonCodec;
    private final AdminOperationRunner operationRunner;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DailyChallengeGeneratorService(
            CountryJdbcRepository countryRepository,
            DailyChallengeJdbcRepository challengeRepository,
            HintSelectorService hintSelectorService,
            ChallengeCountryPoolService countryPoolService,
            CountryVisibilityProfileService visibilityProfileService,
            AppProperties appProperties,
            AppSettingsReader settingsReader,
            ConsoleLogWriter console,
            ChallengeJsonCodec jsonCodec,
            AdminOperationRunner operationRunner,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.countryRepository = countryRepository;
        this.challengeRepository = challengeRepository;
        this.hintSelectorService = hintSelectorService;
        this.countryPoolService = countryPoolService;
        this.visibilityProfileService = visibilityProfileService;
        this.config = appProperties.getChallenge();
        this.settingsReader = settingsReader;
        this.console = console;
        this.jsonCodec = jsonCodec;
        this.operationRunner = operationRunner;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public String startTodayGeneration() {
        return operationRunner.launch(
                new AdminOperationSpec("challenge-today", "Generate Today's Challenges",
                        OperationType.CHALLENGE_GENERATION, "TODAY"),
                context -> summarize(generateTodaysChallenges()));
    }

    public String startDateGeneration(LocalDate date, ChallengeLevel level) {
        String name = level != null
                ? "Generate " + level.getDisplayName() + " for " + date
                : "Generate Challenges for " + date;
        return operationRunner.launch(
                new AdminOperationSpec("challenge-date", name, OperationType.CHALLENGE_GENERATION, "DATE"),
                context -> summarize(level != null
                        ? List.of(generateChallenge(date, level))
                        : generateAllLevelsForDate(date)));
    }

    public String startUpcomingGeneration(int days) {
        return operationRunner.launch(
                new AdminOperationSpec("challenge-upcoming", "Generate Upcoming (" + days + " days)",
                        OperationType.CHALLENGE_GENERATION, "UPCOMING"),
                context -> summarize(generateUpcomingChallenges(days)));
    }

    public List<DailyChallenge> generateTodaysChallenges() {
        return generateAllLevelsForDate(LocalDate.now(clock));
    }

    public List<DailyChallenge> generateUpcomingChallenges(int days) {
        LocalDate startDate = LocalDate.now(clock);
        List<DailyChallenge> allChallenges = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            allChallenges.addAll(generateAllLevelsForDate(startDate.plusDays(i)));
        }
        return allChallenges;
    }

    public List<DailyChallenge> generateAllLevelsForDate(LocalDate date) {
        console.info("Generating challenges for " + date + " (" + ChallengeLevel.values().length + " levels)");
        List<DailyChallenge> challenges = new ArrayList<>();
        List<String> failedLevels = new ArrayList<>();
        Exception firstFailure = null;
        for (ChallengeLevel level : ChallengeLevel.values()) {
            try {
                challenges.add(generateChallenge(date, level));
            } catch (Exception e) {
                failedLevels.add(level.getDisplayName());
                firstFailure = firstFailure != null ? firstFailure : e;
                log.error("Error generating {} challenge for {}", level.getDisplayName(), date, e);
                console.error("Failed to generate " + level.getDisplayName() + " challenge for " + date + ": " + e.getMessage());
            }
        }
        if (!failedLevels.isEmpty()) {
            throw new ChallengeGenerationException(
                    "Challenge generation for " + date + " failed for levels " + failedLevels, firstFailure);
        }
        console.success("Generated " + challenges.size() + " challenges for " + date);
        return challenges;
    }

    public DailyChallenge generateChallenge(LocalDate date, ChallengeLevel level) {
        return transactionTemplate.execute(status -> generateChallengeInTransaction(date, level));
    }

    private DailyChallenge generateChallengeInTransaction(LocalDate date, ChallengeLevel level) {
        challengeRepository.lockSlotForGeneration(date, level);
        var existing = challengeRepository.findByDateAndLevel(date, level);
        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("Generating {} challenge for {}", level.getDisplayName(), date);
        console.info("Generating " + level.getDisplayName() + " challenge for " + date + "...");

        int countriesCount = settingsReader.getCountriesPerChallenge();
        int hintsPerCountry = settingsReader.getHintsPerCountry();
        ChallengeProfile profile = ChallengeProfileFactory.forLevel(level);

        List<Country> selectedCountries = selectCountries(date, level, profile, countriesCount);
        if (selectedCountries.isEmpty()) {
            throw new IllegalStateException("No active countries available for challenge generation");
        }
        if (selectedCountries.size() < countriesCount) {
            log.warn("Could only find {}/{} countries for {} on {}, generating partial challenge",
                    selectedCountries.size(), countriesCount, level, date);
        }

        ChallengePlan plan = ChallengePlanBuilder.build(profile, selectedCountries.size(), hintsPerCountry);
        Map<Country, List<Hint>> hintsByCountry = hintSelectorService.selectHintsForChallenge(plan, selectedCountries, date);

        List<ChallengeCountryEntry> countryEntries = new ArrayList<>();
        List<ChallengeHintEntry> hintEntries = new ArrayList<>();
        int order = 1;
        for (Country country : selectedCountries) {
            int countryOrder = order++;
            countryEntries.add(ChallengeCountryEntry.of(countryOrder, country));
            List<Hint> hints = hintsByCountry.getOrDefault(country, List.of());
            for (int i = 0; i < hints.size(); i++) {
                hintEntries.add(ChallengeHintEntry.of(countryOrder, i + 1, hints.get(i)));
            }
            countryRepository.incrementTimesFeatured(country.getId());
        }

        DailyChallenge challenge = new DailyChallenge();
        challenge.setChallengeDate(date);
        challenge.setChallengeLevel(level);
        challenge.setCountriesJson(jsonCodec.writeCountries(countryEntries));
        challenge.setHintsJson(jsonCodec.writeHints(hintEntries));
        challenge.setDifficulty(calculateOverallDifficulty(selectedCountries, level));
        challenge.setTotalCountries(countryEntries.size());
        challenge.setTotalHints(hintEntries.size());
        challenge.setIsActive(true);
        challenge.setCreatedAt(Instant.now(clock));
        challenge = challengeRepository.save(challenge);

        log.info("Generated {} challenge for {} with {} countries and {} hints",
                level.getDisplayName(), date, challenge.getTotalCountries(), challenge.getTotalHints());
        console.success("Generated " + level.getDisplayName() + " challenge: "
                + challenge.getTotalCountries() + " countries, " + challenge.getTotalHints() + " hints");
        return challenge;
    }

    private List<Country> selectCountries(LocalDate date, ChallengeLevel level, ChallengeProfile profile, int count) {
        List<Country> allCountries = countryRepository.findByIsActiveTrue();
        Set<Long> sameDateIds = getSameDateOtherLevelCountryIds(date, level);
        Set<Long> cooldownIds = getRecentlyUsedCountryIds(date, config.getReuseCooldownDays(), level);

        List<CountrySelectionStep> steps = List.of(
                new CountrySelectionStep("primary profile", profile, true),
                new CountrySelectionStep("expanded visibility tier", ChallengeProfileFactory.expandedForLevel(level), true),
                new CountrySelectionStep("cooldown dropped", profile, false),
                new CountrySelectionStep("any visibility tier", anyTierProfile(profile), false));

        List<Country> selected = new ArrayList<>();
        for (CountrySelectionStep step : steps) {
            if (selected.size() >= count) {
                break;
            }
            if (!selected.isEmpty()) {
                log.warn("{} pool short for {} ({}/{}), trying {}",
                        level.getDisplayName(), date, selected.size(), count, step.description());
            }
            Set<Long> exclude = new HashSet<>(sameDateIds);
            selected.forEach(country -> exclude.add(country.getId()));
            if (step.applyCooldown()) {
                exclude.addAll(cooldownIds);
            }
            selected.addAll(countryPoolService.selectCountries(
                    date, step.profile(), count - selected.size(), exclude, allCountries));
        }
        return selected;
    }

    private static ChallengeProfile anyTierProfile(ChallengeProfile profile) {
        return new ChallengeProfile(
                profile.level(),
                Set.of(VisibilityTier.values()),
                false,
                profile.regionQuotas(),
                profile.hintBandRatios(),
                profile.imageQuotaRule(),
                profile.difficultyCurve(),
                profile.minDifficulty());
    }

    public int deactivateOldChallenges(int olderThanDays) {
        int count = challengeRepository.deactivateOlderThan(LocalDate.now(clock).minusDays(olderThanDays));
        log.info("Deactivated {} old challenges", count);
        return count;
    }

    private Difficulty calculateOverallDifficulty(List<Country> countries, ChallengeLevel level) {
        double avgScore = countries.stream()
                .mapToDouble(c -> visibilityProfileService.getProfile(c.getIsoAlpha2()).score())
                .average()
                .orElse(1.0);

        return switch (level) {
            case EASY -> avgScore <= 0.45 ? Difficulty.EASY : Difficulty.MEDIUM;
            case HARD -> avgScore >= 1.0 ? Difficulty.EXPERT : Difficulty.HARD;
            default -> {
                if (avgScore <= 0.45) yield Difficulty.EASY;
                else if (avgScore <= 0.75) yield Difficulty.MEDIUM;
                else if (avgScore <= 1.1) yield Difficulty.HARD;
                else yield Difficulty.EXPERT;
            }
        };
    }

    private Set<Long> getSameDateOtherLevelCountryIds(LocalDate date, ChallengeLevel currentLevel) {
        Set<Long> ids = new HashSet<>();
        for (ChallengeLevel otherLevel : ChallengeLevel.values()) {
            if (otherLevel == currentLevel) continue;
            challengeRepository.findByDateAndLevel(date, otherLevel)
                    .ifPresent(c -> ids.addAll(countryIds(c)));
        }
        return ids;
    }

    private Set<Long> getRecentlyUsedCountryIds(LocalDate challengeDate, int days, ChallengeLevel level) {
        LocalDate cutoffDate = challengeDate.minusDays(days);
        LocalDate endDate = challengeDate.minusDays(1);
        if (endDate.isBefore(cutoffDate)) {
            return Set.of();
        }
        return challengeRepository.findByDateRangeAndLevel(cutoffDate, endDate, level).stream()
                .flatMap(challenge -> countryIds(challenge).stream())
                .collect(Collectors.toSet());
    }

    private Set<Long> countryIds(DailyChallenge challenge) {
        return jsonCodec.readCountries(challenge.getCountriesJson()).stream()
                .map(ChallengeCountryEntry::countryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static OperationSummary summarize(List<DailyChallenge> challenges) {
        return OperationSummary.of("challengesGenerated", challenges.size())
                .and("challenges", challenges.stream()
                        .map(c -> c.getChallengeDate() + " " + c.getChallengeLevel()
                                + ": " + c.getTotalCountries() + " countries, " + c.getTotalHints() + " hints")
                        .toList());
    }
}
