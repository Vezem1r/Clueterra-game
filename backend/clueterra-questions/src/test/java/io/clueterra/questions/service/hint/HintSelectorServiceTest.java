package io.clueterra.questions.service.hint;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.GameMode;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.jdbc.HintUsageLogJdbcRepository;
import io.clueterra.questions.service.challenge.ChallengeHintAllocationService;
import io.clueterra.questions.service.challenge.ChallengePlan;
import io.clueterra.questions.service.challenge.HintCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HintSelectorServiceTest {

    @Test
    void usesChallengeDateForCooldownFilteringAndUsageLogging() {
        HintRepository hintRepository = mock(HintRepository.class);
        HintUsageLogJdbcRepository usageLogRepository = mock(HintUsageLogJdbcRepository.class);
        ChallengeHintAllocationService allocationService = mock(ChallengeHintAllocationService.class);

        AppProperties properties = new AppProperties();
        properties.getHints().setReuseCooldownDays(30);

        HintSelectorService service = new HintSelectorService(
                hintRepository,
                usageLogRepository,
                allocationService,
                properties
        );

        LocalDate challengeDate = LocalDate.of(2026, 4, 20);
        Country country = Country.builder()
                .id(10L)
                .isoAlpha2("JP")
                .nameEn("Japan")
                .build();
        Hint fresh = hint(1L, "Fresh");
        Hint stale = hint(2L, "Stale");
        ChallengePlan plan = new ChallengePlan(
                null,
                1,
                1,
                1,
                Map.of(HintBand.EASY_HINT, 1),
                0,
                new int[]{20},
                new HintBand[][]{{HintBand.EASY_HINT}},
                0
        );

        when(hintRepository.findActiveByCountryId(country.getId())).thenReturn(List.of(fresh, stale));
        when(usageLogRepository.findRecentlyUsedHintIds(
                country.getId(),
                GameMode.DAILY,
                challengeDate.minusDays(30)
        )).thenReturn(List.of(2L));
        when(allocationService.allocateHints(eq(plan), any())).thenReturn(Map.of(country, List.of(fresh)));

        service.selectHintsForChallenge(plan, List.of(country), challengeDate);

        ArgumentCaptor<Map<Country, List<HintCandidate>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(allocationService).allocateHints(eq(plan), captor.capture());
        assertThat(captor.getValue().get(country))
                .extracting(candidate -> candidate.hint().getId())
                .containsExactly(1L);

        verify(usageLogRepository).findRecentlyUsedHintIds(
                country.getId(),
                GameMode.DAILY,
                challengeDate.minusDays(30)
        );
        verify(usageLogRepository).saveBatch(List.of(1L), country.getId(), challengeDate, GameMode.DAILY);
        verify(hintRepository).batchMarkUsed(List.of(1L));
    }

    private Hint hint(Long id, String text) {
        return Hint.builder()
                .id(id)
                .countryId(10L)
                .hintText(text)
                .difficulty(20)
                .qualityScore(new BigDecimal("0.90"))
                .category(Category.GEOGRAPHY)
                .hintType(HintType.ABSOLUTE_FACT)
                .source(DataSource.NAGER_DATE)
                .sourceIndicator("HOLIDAY_" + id)
                .build();
    }
}
