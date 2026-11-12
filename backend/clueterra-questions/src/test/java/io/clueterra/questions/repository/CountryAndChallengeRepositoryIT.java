package io.clueterra.questions.repository;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.Difficulty;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;
import io.clueterra.questions.support.ClockTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_schema,cl_ai_schema,cl_sch_schema,cl_staging",
        "spring.flyway.default-schema=cl_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_schema, cl_ai_schema, cl_sch_schema, cl_staging"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ClockTestConfiguration.class,
        CountryJdbcRepository.class, DailyChallengeJdbcRepository.class, HintRepository.class})
class CountryAndChallengeRepositoryIT {

    @Autowired
    private CountryJdbcRepository countryRepository;

    @Autowired
    private DailyChallengeJdbcRepository challengeRepository;

    @Autowired
    private HintRepository hintRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void fillMissingFromSource_neverOverwritesAdminEditsOrReactivatesCountry() {
        Country inserted = countryRepository.insert(Country.builder()
                .isoAlpha2("XA").isoAlpha3("XAA").nameEn("Test Qatar").capital("Doha")
                .difficultyModifier(new BigDecimal("1.20")).isActive(true).build());
        jdbc.update("UPDATE countries SET is_active = false, capital = 'Admin Capital' WHERE id = :id",
                Map.of("id", inserted.getId()));
        Country existing = countryRepository.findByIsoAlpha2("xa").orElseThrow();

        Country merged = countryRepository.fillMissingFromSource(existing, Country.builder()
                .nameEn("State of Qatar").capital("Doha").flagUrl("https://flags/qa.svg").build());

        assertThat(merged.getIsActive()).isFalse();
        assertThat(merged.getNameEn()).isEqualTo("Test Qatar");
        assertThat(merged.getCapital()).isEqualTo("Admin Capital");
        assertThat(merged.getFlagUrl()).isEqualTo("https://flags/qa.svg");
        assertThat(merged.getDifficultyModifier()).isEqualByComparingTo("1.20");
    }

    @Test
    void timesFeaturedIsAdjustedAtomicallyAndNeverNegative() {
        Country country = countryRepository.insert(Country.builder()
                .isoAlpha2("XB").isoAlpha3("XBB").nameEn("Test Oman").difficultyModifier(BigDecimal.ONE).isActive(true).build());

        countryRepository.incrementTimesFeatured(country.getId());
        countryRepository.incrementTimesFeatured(country.getId());
        countryRepository.adjustTimesFeatured(country.getId(), -5);

        assertThat(countryRepository.findById(country.getId()).orElseThrow().getTimesFeatured()).isZero();
    }

    @Test
    void challengeSlotLockAndBulkDeactivationWork() {
        LocalDate oldDate = LocalDate.of(2020, 1, 1);
        challengeRepository.save(challenge(oldDate));
        challengeRepository.save(challenge(oldDate.plusDays(1)));

        challengeRepository.lockSlotForGeneration(oldDate, ChallengeLevel.EASY);
        int deactivated = challengeRepository.deactivateOlderThan(LocalDate.of(2020, 1, 2));

        assertThat(deactivated).isEqualTo(1);
        assertThat(challengeRepository.findByDateAndLevel(oldDate, ChallengeLevel.EASY).orElseThrow().getIsActive()).isFalse();
        assertThat(challengeRepository.findByIdForUpdate(
                challengeRepository.findByDateAndLevel(oldDate.plusDays(1), ChallengeLevel.EASY).orElseThrow().getId()))
                .get().extracting(DailyChallenge::getIsActive).isEqualTo(true);
    }

    @Test
    void coverageOfCountryWithoutHintsIsEmpty() {
        Country country = countryRepository.insert(Country.builder()
                .isoAlpha2("XC").isoAlpha3("XCC").nameEn("Test Bahrain").difficultyModifier(BigDecimal.ONE).isActive(true).build());

        HintCoverageStats coverage = hintRepository.findActiveCoverageByCountryId(country.getId());

        assertThat(coverage.categoriesCovered()).isZero();
        assertThat(coverage.avgDifficulty()).isNull();
    }

    private static DailyChallenge challenge(LocalDate date) {
        DailyChallenge challenge = new DailyChallenge();
        challenge.setChallengeDate(date);
        challenge.setChallengeLevel(ChallengeLevel.EASY);
        challenge.setCountriesJson("[]");
        challenge.setHintsJson("[]");
        challenge.setDifficulty(Difficulty.EASY);
        challenge.setTotalCountries(0);
        challenge.setTotalHints(0);
        challenge.setIsActive(true);
        return challenge;
    }
}
