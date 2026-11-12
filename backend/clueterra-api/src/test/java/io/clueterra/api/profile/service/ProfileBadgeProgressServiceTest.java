package io.clueterra.api.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.profile.dto.ProfileBadgeDto;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeCountryProgressRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeGrantConfigRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeMissingCountryRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressKind;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressStatsRow;
import io.clueterra.api.profile.repository.ProfileBadgeProgressJdbcRepository;

@ExtendWith(MockitoExtension.class)
class ProfileBadgeProgressServiceTest {

    private static final long USER_ID = 42L;

    @Mock
    private ProfileBadgeProgressJdbcRepository progressRepository;

    @Test
    @DisplayName("country collection progress exposes backend-formatted counts and missing country preview")
    void countryCollectionProgressIncludesMissingPreview() {
        ProfileBadgeProgressService service =
                new ProfileBadgeProgressService(progressRepository, new ObjectMapper());
        ProfileBadgeDto badge = badge(1L, "asia-master");

        when(progressRepository.findGrantConfigs(List.of(1L))).thenReturn(List.of(new ProfileBadgeGrantConfigRow(
                1L,
                "TEMPLATE",
                "{\"trigger\":\"REGION_COMPLETED\",\"threshold\":\"Asia\"}")));
        when(progressRepository.loadUserStats(USER_ID)).thenReturn(stats());
        when(progressRepository.loadCountryProgress(USER_ID, "REGION", "Asia"))
                .thenReturn(new ProfileBadgeCountryProgressRow(50, 16));
        when(progressRepository.findMissingCountries(USER_ID, "REGION", "Asia", 2)).thenReturn(List.of(
                new ProfileBadgeMissingCountryRow("AF", "Afghanistan", "AF", "/api/assets/flags/af.svg"),
                new ProfileBadgeMissingCountryRow("AM", "Armenia", "AM", "/api/assets/flags/am.svg")));

        List<ProfileBadgeDto> result = service.attachProgress(USER_ID, List.of(badge));

        assertThat(result).singleElement().satisfies(withProgress -> {
            assertThat(withProgress.progress().kind()).isEqualTo(ProfileBadgeProgressKind.COUNTRY_COLLECTION);
            assertThat(withProgress.progress().current()).isEqualTo(16);
            assertThat(withProgress.progress().target()).isEqualTo(50);
            assertThat(withProgress.progress().remaining()).isEqualTo(34);
            assertThat(withProgress.progress().percent()).isEqualTo(32);
            assertThat(withProgress.progress().currentDisplay()).isEqualTo("16");
            assertThat(withProgress.progress().targetDisplay()).isEqualTo("50");
            assertThat(withProgress.progress().remainingDisplay()).isEqualTo("34");
            assertThat(withProgress.progress().hiddenMissingCount()).isEqualTo(32);
            assertThat(withProgress.progress().hiddenMissingCountDisplay()).isEqualTo("32");
            assertThat(withProgress.progress().missingItems())
                    .extracting("code", "name", "flagUrl")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("AF", "Afghanistan", "/api/assets/flags/af.svg"),
                            org.assertj.core.groups.Tuple.tuple("AM", "Armenia", "/api/assets/flags/am.svg"));
        });
    }

    @Test
    @DisplayName("perfect-game progress comes from projected perfectRuns counter")
    void perfectGameProgressUsesProjectedPerfectRuns() {
        ProfileBadgeProgressService service =
                new ProfileBadgeProgressService(progressRepository, new ObjectMapper());
        ProfileBadgeDto badge = badge(2L, "perfect-run");

        when(progressRepository.findGrantConfigs(List.of(2L))).thenReturn(List.of(new ProfileBadgeGrantConfigRow(
                2L,
                "TEMPLATE",
                "{\"trigger\":\"PERFECT_GAME\"}")));
        when(progressRepository.loadUserStats(USER_ID))
                .thenReturn(new ProfileBadgeProgressStatsRow(
                        500, 3, 4, 3, 2, 5, 2,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        List<ProfileBadgeDto> result = service.attachProgress(USER_ID, List.of(badge));

        assertThat(result).singleElement().satisfies(withProgress -> {
            assertThat(withProgress.progress().kind()).isEqualTo(ProfileBadgeProgressKind.COUNTER);
            assertThat(withProgress.progress().current()).isEqualTo(2);
            assertThat(withProgress.progress().target()).isEqualTo(1);
            assertThat(withProgress.progress().remaining()).isZero();
            assertThat(withProgress.progress().percent()).isEqualTo(100);
            assertThat(withProgress.progress().currentDisplay()).isEqualTo("2");
        });
        verify(progressRepository, never()).loadCountryProgress(USER_ID, "ALL", null);
    }

    @Test
    @DisplayName("mastered project value progress is formatted as compact USD")
    void masteredValueProgressUsesCurrencyDisplay() {
        ProfileBadgeProgressService service =
                new ProfileBadgeProgressService(progressRepository, new ObjectMapper());
        ProfileBadgeDto badge = badge(3L, "trillion-club");

        when(progressRepository.findGrantConfigs(List.of(3L))).thenReturn(List.of(new ProfileBadgeGrantConfigRow(
                3L,
                "TEMPLATE",
                "{\"trigger\":\"BUDGET_MASTERED_VALUE_USD\",\"threshold\":1000000000000}")));
        when(progressRepository.loadUserStats(USER_ID)).thenReturn(new ProfileBadgeProgressStatsRow(
                0, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 124_700_000_000L, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        List<ProfileBadgeDto> result = service.attachProgress(USER_ID, List.of(badge));

        assertThat(result).singleElement().satisfies(withProgress -> {
            assertThat(withProgress.progress().currentDisplay()).isEqualTo("$125B");
            assertThat(withProgress.progress().targetDisplay()).isEqualTo("$1T");
            assertThat(withProgress.progress().percent()).isEqualTo(12);
        });
    }

    @Test
    void riverBadgeProgressUsesTheSharedStatsSnapshot() {
        ProfileBadgeProgressService service =
                new ProfileBadgeProgressService(progressRepository, new ObjectMapper());
        List<ProfileBadgeDto> badges = List.of(badge(4L, "river-attempted"), badge(5L, "river-solved"));
        when(progressRepository.findGrantConfigs(List.of(4L, 5L))).thenReturn(List.of(
                new ProfileBadgeGrantConfigRow(4L, "TEMPLATE",
                        "{\"trigger\":\"RIVER_UNIQUE_ATTEMPTED\",\"threshold\":250}"),
                new ProfileBadgeGrantConfigRow(5L, "TEMPLATE",
                        "{\"trigger\":\"RIVER_UNIQUE_SOLVED\",\"threshold\":50}")));
        when(progressRepository.loadUserStats(USER_ID)).thenReturn(new ProfileBadgeProgressStatsRow(
                0, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 10, 0, 0, 0, 0, 0, 0));

        List<ProfileBadgeDto> result = service.attachProgress(USER_ID, badges);

        assertThat(result).extracting(item -> item.progress().current()).containsExactly(250L, 10L);
        verify(progressRepository).loadUserStats(USER_ID);
    }

    private static ProfileBadgeProgressStatsRow stats() {
        return new ProfileBadgeProgressStatsRow(
                1_250, 4, 10, 8, 3, 7, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ProfileBadgeDto badge(Long id, String slug) {
        return new ProfileBadgeDto(
                id,
                slug,
                "/badges/" + slug + ".png",
                "gold",
                "green",
                slug,
                "description",
                false,
                null,
                null);
    }
}
