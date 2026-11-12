package io.clueterra.questions.service.challenge;

import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.config.ImageQuotaRule;
import io.clueterra.questions.config.RegionQuota;
import io.clueterra.questions.config.SlotDifficultyCurve;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.RegionGroup;
import io.clueterra.questions.entity.enums.VisibilityTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeCountryPoolServiceTest {

    @Test
    void prefersFreshAndFamiliarCountriesInsideTheSameRegionPool() {
        CountryVisibilityProfileService profileService = mock(CountryVisibilityProfileService.class);
        ChallengeCountryPoolService service = new ChallengeCountryPoolService(profileService);

        Country familiar = Country.builder()
                .id(1L)
                .isoAlpha2("US")
                .nameEn("United States")
                .isActive(true)
                .timesFeatured(0)
                .build();
        Country overused = Country.builder()
                .id(2L)
                .isoAlpha2("LI")
                .nameEn("Liechtenstein")
                .isActive(true)
                .timesFeatured(50)
                .build();

        when(profileService.getProfile("US")).thenReturn(
                new io.clueterra.questions.config.CountryVisibilityProfile(0.20, VisibilityTier.EASY, true, RegionGroup.EUROPE)
        );
        when(profileService.getProfile("LI")).thenReturn(
                new io.clueterra.questions.config.CountryVisibilityProfile(1.20, VisibilityTier.HARD, false, RegionGroup.EUROPE)
        );

        ChallengeProfile profile = new ChallengeProfile(
                ChallengeLevel.EASY,
                Set.of(VisibilityTier.EASY, VisibilityTier.HARD),
                false,
                Map.of(RegionGroup.EUROPE, new RegionQuota(0, 1)),
                Map.of(HintBand.EASY_HINT, 1.0),
                new ImageQuotaRule(),
                new SlotDifficultyCurve(32, 5),
                0
        );

        List<Country> selected = service.selectCountries(
                LocalDate.of(2026, 4, 12),
                profile,
                1,
                Set.of(),
                List.of(familiar, overused)
        );

        assertThat(selected)
                .extracting(Country::getIsoAlpha2)
                .containsExactly("US");
    }
}
