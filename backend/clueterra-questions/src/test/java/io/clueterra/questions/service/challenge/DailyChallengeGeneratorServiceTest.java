package io.clueterra.questions.service.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.clueterra.questions.config.AppSettingsReader;
import io.clueterra.questions.config.CountryVisibilityProfile;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.hint.HintSelectorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyChallengeGeneratorServiceTest {

    @Test
    void generateChallengeUsesChallengeDateForCooldownQueriesAndHintSelection() {
        CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
        DailyChallengeJdbcRepository challengeRepository = mock(DailyChallengeJdbcRepository.class);
        HintSelectorService hintSelectorService = mock(HintSelectorService.class);
        ChallengeCountryPoolService countryPoolService = mock(ChallengeCountryPoolService.class);
        CountryVisibilityProfileService visibilityProfileService = mock(CountryVisibilityProfileService.class);
        AppSettingsReader settingsReader = mock(AppSettingsReader.class);
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);

        AppProperties properties = new AppProperties();
        properties.getChallenge().setReuseCooldownDays(30);

        DailyChallengeGeneratorService service = new DailyChallengeGeneratorService(
                countryRepository,
                challengeRepository,
                hintSelectorService,
                countryPoolService,
                visibilityProfileService,
                properties,
                settingsReader,
                console,
                new ChallengeJsonCodec(new ObjectMapper()),
                mock(AdminOperationRunner.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        LocalDate challengeDate = LocalDate.of(2026, 5, 10);
        Country country = Country.builder()
                .id(1L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .timesFeatured(0)
                .build();
        Hint hint = Hint.builder()
                .id(10L)
                .countryId(country.getId())
                .hintText("flag")
                .difficulty(20)
                .qualityScore(new BigDecimal("0.90"))
                .category(Category.CULTURE)
                .hintType(HintType.IMAGE_FACT)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_FLAG_IMAGE_1")
                .build();

        when(challengeRepository.findByDateAndLevel(challengeDate, ChallengeLevel.EASY)).thenReturn(Optional.empty());
        when(challengeRepository.findByDateRangeAndLevel(
                challengeDate.minusDays(30),
                challengeDate.minusDays(1),
                ChallengeLevel.EASY
        )).thenReturn(List.of());
        when(countryRepository.findByIsActiveTrue()).thenReturn(List.of(country));
        when(countryPoolService.selectCountries(eq(challengeDate), any(), eq(1), eq(Set.of()), eq(List.of(country))))
                .thenReturn(List.of(country));
        when(settingsReader.getCountriesPerChallenge()).thenReturn(1);
        when(settingsReader.getHintsPerCountry()).thenReturn(1);
        when(visibilityProfileService.getProfile("US"))
                .thenReturn(new CountryVisibilityProfile(0.20, VisibilityTier.EASY, true, RegionGroup.AMERICAS));
        when(hintSelectorService.selectHintsForChallenge(any(), eq(List.of(country)), eq(challengeDate)))
                .thenReturn(Map.of(country, List.of(hint)));
        when(challengeRepository.save(any(DailyChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.generateChallenge(challengeDate, ChallengeLevel.EASY);

        verify(challengeRepository).findByDateRangeAndLevel(
                challengeDate.minusDays(30),
                challengeDate.minusDays(1),
                ChallengeLevel.EASY
        );
        verify(hintSelectorService).selectHintsForChallenge(any(), eq(List.of(country)), eq(challengeDate));
        verify(challengeRepository).lockSlotForGeneration(challengeDate, ChallengeLevel.EASY);
        verify(countryRepository).incrementTimesFeatured(country.getId());
    }
}
