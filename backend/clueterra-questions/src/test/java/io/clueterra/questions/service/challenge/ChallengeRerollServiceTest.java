package io.clueterra.questions.service.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.AppSettingsReader;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.Difficulty;
import io.clueterra.questions.entity.enums.GameMode;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.repository.jdbc.DailyChallengeJdbcRepository;
import io.clueterra.questions.repository.jdbc.HintUsageLogJdbcRepository;
import io.clueterra.questions.service.hint.HintSelectorService;

class ChallengeRerollServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 10);
    private static final String COUNTRIES_JSON = """
            [{"order":1,"countryId":10,"code":"DE","name":"Germany"},{"order":2,"countryId":20,"code":"FR","name":"France"}]
            """;
    private static final String HINTS_JSON = """
            [{"countryOrder":1,"hintOrder":1,"hintId":101,"text":"Old DE 1","difficulty":30,"category":"GEOGRAPHY","type":"ABSOLUTE_FACT","source":"CIA_FACTBOOK"},
            {"countryOrder":1,"hintOrder":2,"hintId":102,"text":"Old DE 2","difficulty":35,"category":"ECONOMY","type":"RANKING","source":"WORLD_BANK"},
            {"countryOrder":2,"hintOrder":1,"hintId":201,"text":"Old FR 1","difficulty":25,"category":"CULTURE","type":"CULTURAL","source":"MANUAL"},
            {"countryOrder":2,"hintOrder":2,"hintId":202,"text":"Old FR 2","difficulty":45,"category":"HISTORY","type":"HISTORICAL","source":"WIKIDATA"}]
            """;

    private final DailyChallengeJdbcRepository challengeRepository = mock(DailyChallengeJdbcRepository.class);
    private final CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final HintUsageLogJdbcRepository usageLogRepository = mock(HintUsageLogJdbcRepository.class);
    private final HintSelectorService hintSelectorService = mock(HintSelectorService.class);
    private final ChallengeCountryPoolService countryPoolService = mock(ChallengeCountryPoolService.class);
    private final AppSettingsReader settingsReader = mock(AppSettingsReader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);

    private final ChallengeRerollService service = new ChallengeRerollService(
            challengeRepository,
            countryRepository,
            hintRepository,
            usageLogRepository,
            hintSelectorService,
            countryPoolService,
            settingsReader,
            new ChallengeJsonCodec(objectMapper),
            new AppProperties(),
            clock);

    @Test
    void rerollHintRejectsPastChallenge() {
        when(challengeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(challenge(TODAY.minusDays(1))));

        assertThatThrownBy(() -> service.rerollHint(1L, 1, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify past challenge");

        verify(challengeRepository, never()).update(any(DailyChallenge.class));
    }

    @Test
    void rerollCountryRejectsTodaysChallenge() {
        when(challengeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(challenge(TODAY)));

        assertThatThrownBy(() -> service.rerollCountry(1L, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reroll countries in today's challenge");

        verify(challengeRepository, never()).update(any(DailyChallenge.class));
    }

    @Test
    void rerollHintForTodayReplacesSingleHintWithoutChangingChallengeShape() throws Exception {
        DailyChallenge challenge = challenge(TODAY);
        Hint replacement = hint(999L, 10L, "New DE image hint", Category.LANDMARKS, HintType.IMAGE_FACT, DataSource.MANUAL, "hints/de-landmark.jpg");

        when(challengeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(challenge));
        when(usageLogRepository.findRecentlyUsedHintIds(10L, GameMode.DAILY, TODAY.minusDays(30))).thenReturn(List.of());
        when(hintRepository.findActiveByCountryId(10L)).thenReturn(List.of(replacement));

        service.rerollHint(1L, 1, 2);

        DailyChallenge updated = captureUpdatedChallenge();
        List<Map<String, Object>> hints = readJson(updated.getHintsJson());

        assertThat(updated.getCountriesJson()).isEqualTo(COUNTRIES_JSON);
        assertThat(updated.getTotalCountries()).isEqualTo(2);
        assertThat(updated.getTotalHints()).isEqualTo(4);
        assertThat(hints).hasSize(4);
        assertThat(hints)
                .filteredOn(h -> intValue(h, "countryOrder") == 1 && intValue(h, "hintOrder") == 2)
                .singleElement()
                .satisfies(h -> {
                    assertThat(longValue(h, "hintId")).isEqualTo(999L);
                    assertThat(h.get("text")).isEqualTo("New DE image hint");
                    assertThat(h.get("imageUrl")).isEqualTo("hints/de-landmark.jpg");
                });
        assertThat(hints)
                .filteredOn(h -> intValue(h, "countryOrder") == 2)
                .hasSize(2);
        verify(hintSelectorService).markHintsAsUsed(List.of(replacement));
        verify(usageLogRepository).deleteBatch(List.of(102L), TODAY, GameMode.DAILY);
        verify(usageLogRepository).save(999L, 10L, TODAY, GameMode.DAILY, 1L);
        verify(hintRepository).batchUnmarkUsed(List.of(102L));
    }

    @Test
    void rerollCountryForFutureChallengeReplacesCountryAndOnlyThatCountriesHints() throws Exception {
        DailyChallenge challenge = challenge(TODAY.plusDays(1));
        Country newCountry = Country.builder()
                .id(30L)
                .isoAlpha2("ES")
                .nameEn("Spain")
                .build();
        List<Hint> newHints = List.of(
                hint(301L, 30L, "New ES 1", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, DataSource.CIA_FACTBOOK, null),
                hint(302L, 30L, "New ES 2", Category.CULTURE, HintType.IMAGE_FACT, DataSource.MANUAL, "hints/es-culture.jpg"));

        when(challengeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(challenge));
        when(countryRepository.findByIsActiveTrue()).thenReturn(List.of(newCountry));
        when(countryPoolService.selectCountries(eq(TODAY.plusDays(1)), any(), eq(1), anySet(), anyList()))
                .thenReturn(List.of(newCountry));
        when(settingsReader.getHintsPerCountry()).thenReturn(2);
        when(hintSelectorService.selectHintsForChallenge(any(), eq(List.of(newCountry)), eq(TODAY.plusDays(1))))
                .thenReturn(Map.of(newCountry, newHints));

        service.rerollCountry(1L, 2);

        DailyChallenge updated = captureUpdatedChallenge();
        List<Map<String, Object>> countries = readJson(updated.getCountriesJson());
        List<Map<String, Object>> hints = readJson(updated.getHintsJson());

        assertThat(updated.getTotalCountries()).isEqualTo(2);
        assertThat(updated.getTotalHints()).isEqualTo(4);
        assertThat(countries)
                .filteredOn(c -> intValue(c, "order") == 2)
                .singleElement()
                .satisfies(c -> {
                    assertThat(longValue(c, "countryId")).isEqualTo(30L);
                    assertThat(c.get("code")).isEqualTo("ES");
                    assertThat(c.get("name")).isEqualTo("Spain");
                });
        assertThat(hints)
                .filteredOn(h -> intValue(h, "countryOrder") == 2)
                .extracting(h -> longValue(h, "hintId"))
                .containsExactly(301L, 302L);
        assertThat(hints)
                .filteredOn(h -> intValue(h, "countryOrder") == 1)
                .hasSize(2);
        verify(usageLogRepository).deleteBatch(List.of(201L, 202L), TODAY.plusDays(1), GameMode.DAILY);
        verify(hintRepository).batchUnmarkUsed(List.of(201L, 202L));
        verify(countryRepository).adjustTimesFeatured(20L, -1);
        verify(countryRepository).incrementTimesFeatured(30L);
    }

    private DailyChallenge captureUpdatedChallenge() {
        ArgumentCaptor<DailyChallenge> captor = ArgumentCaptor.forClass(DailyChallenge.class);
        verify(challengeRepository).update(captor.capture());
        return captor.getValue();
    }

    private DailyChallenge challenge(LocalDate date) {
        return DailyChallenge.builder()
                .id(1L)
                .challengeDate(date)
                .challengeLevel(ChallengeLevel.EASY)
                .difficulty(Difficulty.EASY)
                .countriesJson(COUNTRIES_JSON)
                .hintsJson(HINTS_JSON)
                .totalCountries(2)
                .totalHints(4)
                .isActive(true)
                .build();
    }

    private Hint hint(Long id, Long countryId, String text, Category category, HintType type, DataSource source, String imageUrl) {
        return Hint.builder()
                .id(id)
                .countryId(countryId)
                .hintText(text)
                .category(category)
                .hintType(type)
                .source(source)
                .difficulty(40)
                .qualityScore(new BigDecimal("0.90"))
                .imageUrl(imageUrl)
                .build();
    }

    private List<Map<String, Object>> readJson(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private int intValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private long longValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
