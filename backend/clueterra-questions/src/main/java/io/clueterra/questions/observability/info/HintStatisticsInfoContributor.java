package io.clueterra.questions.observability.info;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HintStatisticsInfoContributor implements InfoContributor {

    private final HintRepository hintRepository;
    private final CountryJdbcRepository countryRepository;
    private final DailyChallengeJdbcRepository challengeRepository;

    @Override
    public void contribute(Info.Builder builder) {
        try {
            Map<String, Object> stats = new HashMap<>();
            Map<String, Object> countryStats = buildCountryStats();
            stats.put("countries", countryStats);
            Map<String, Object> hintStats = buildHintStats(countryStats);
            stats.put("hints", hintStats);
            Map<String, Object> challengeStats = buildChallengeStats();
            stats.put("challenges", challengeStats);
            builder.withDetail("statistics", stats);
        } catch (Exception e) {
            log.error("Failed to contribute statistics", e);
            builder.withDetail("statistics", Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildCountryStats() {
        Map<String, Object> countryStats = new HashMap<>();
        long totalCountries = countryRepository.count();
        long activeCountries = countryRepository.countActive();
        countryStats.put("total", totalCountries);
        countryStats.put("active", activeCountries);
        countryStats.put("inactive", totalCountries - activeCountries);
        return countryStats;
    }

    private Map<String, Object> buildHintStats(Map<String, Object> countryStats) {
        Map<String, Object> hintStats = new HashMap<>();
        long totalHints = hintRepository.count();
        long activeHints = hintRepository.countActive();
        long activeCountries = (Long) countryStats.get("active");
        hintStats.put("total", totalHints);
        hintStats.put("active", activeHints);
        hintStats.put("inactive", totalHints - activeHints);

        double avgHintsPerCountry = activeCountries > 0 ? (double) activeHints / activeCountries : 0;
        hintStats.put("avgPerCountry", String.format("%.2f", avgHintsPerCountry));
        return hintStats;
    }

    private Map<String, Object> buildChallengeStats() {
        Map<String, Object> challengeStats = new HashMap<>();
        long totalChallenges = challengeRepository.count();
        long activeChallenges = challengeRepository.countActive();

        challengeStats.put("total", totalChallenges);
        challengeStats.put("active", activeChallenges);
        return challengeStats;
    }
}
