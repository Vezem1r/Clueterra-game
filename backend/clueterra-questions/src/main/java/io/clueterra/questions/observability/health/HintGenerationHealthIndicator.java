package io.clueterra.questions.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HintGenerationHealthIndicator implements HealthIndicator {

    private final HintRepository hintRepository;
    private final CountryJdbcRepository countryRepository;

    private static final int MIN_HINTS_PER_COUNTRY = 5;

    @Override
    public Health health() {
        try {
            long totalCountries = countryRepository.count();
            long activeCountries = countryRepository.countActive();
            long totalHints = hintRepository.count();
            long activeHints = hintRepository.countActive();

            double avgHintsPerCountry = activeCountries > 0
                    ? (double) activeHints / activeCountries
                    : 0;

            if (avgHintsPerCountry < MIN_HINTS_PER_COUNTRY && activeCountries > 0) {
                return Health.down()
                        .withDetail("status", "INSUFFICIENT_HINTS")
                        .withDetail("totalCountries", totalCountries)
                        .withDetail("activeCountries", activeCountries)
                        .withDetail("totalHints", totalHints)
                        .withDetail("activeHints", activeHints)
                        .withDetail("avgHintsPerCountry", String.format("%.2f", avgHintsPerCountry))
                        .withDetail("minRequired", MIN_HINTS_PER_COUNTRY)
                        .withDetail("message", "Insufficient hints - run sync to generate more")
                        .build();
            }

            String message = activeCountries == 0
                    ? "No countries loaded yet - run sync first"
                    : "Hint generation system healthy";

            return Health.up()
                    .withDetail("status", "HEALTHY")
                    .withDetail("totalCountries", totalCountries)
                    .withDetail("activeCountries", activeCountries)
                    .withDetail("totalHints", totalHints)
                    .withDetail("activeHints", activeHints)
                    .withDetail("avgHintsPerCountry", String.format("%.2f", avgHintsPerCountry))
                    .withDetail("message", message)
                    .build();

        } catch (Exception e) {
            log.error("Hint generation health check failed", e);
            return Health.down()
                    .withDetail("status", "ERROR")
                    .withDetail("error", e.getMessage())
                    .withDetail("message", "Hint generation health check failed")
                    .build();
        }
    }
}
