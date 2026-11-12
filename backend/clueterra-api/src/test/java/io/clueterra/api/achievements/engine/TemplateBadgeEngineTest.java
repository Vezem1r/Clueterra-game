package io.clueterra.api.achievements.engine;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.clueterra.api.admin.settings.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

@ExtendWith(MockitoExtension.class)
class TemplateBadgeEngineTest {

    @Mock
    private BadgeEngineJdbcRepository engineRepo;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private TemplateBadgeEngine engine;

    @BeforeEach
    void injectObjectMapper() {
        engine = new TemplateBadgeEngine(engineRepo, new ObjectMapper(), settingsService);
    }

    private static final long USER_ID = 42L;

    @Nested
    @DisplayName("evaluateForUser - early exits")
    class EarlyExits {

        @Test
        @DisplayName("does nothing when user has no unclaimed template badges")
        void noBadges_skipsEvaluation() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of());
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo, never()).loadUserStats(anyLong());
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("does nothing when user stats cannot be loaded (null)")
        void nullStats_skipsEvaluation() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(1L, "{\"trigger\":\"FIRST_WIN\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(null);
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("exception during evaluation is swallowed - does not propagate to caller")
        void exceptionInEvaluation_doesNotPropagate() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenThrow(new RuntimeException("DB is down"));
            org.assertj.core.api.Assertions.assertThatCode(
                            () -> engine.evaluateForUser(USER_ID, completedSession(true, 1)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("trigger: FIRST_WIN")
    class FirstWin {

        @Test
        @DisplayName("badge granted when user has at least 1 win")
        void onWin_badgeGranted() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(10L, "{\"trigger\":\"FIRST_WIN\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(1, 1));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo).grantBadge(USER_ID, 10L, 0, 0);
        }

        @Test
        @DisplayName("badge NOT granted when user has zero wins")
        void noWinsYet_badgeNotGranted() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(10L, "{\"trigger\":\"FIRST_WIN\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(1, 0));
            engine.evaluateForUser(USER_ID, completedSession(false, 2));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("trigger: PERFECT_GAME")
    class PerfectGame {

        @Test
        @DisplayName("badge granted when all countries guessed on the very first attempt")
        void allCountriesGuessedOnFirstAttempt_isPerfect() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(20L, "{\"trigger\":\"PERFECT_GAME\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(5, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo).grantBadge(USER_ID, 20L, 0, 0);
        }

        @Test
        @DisplayName("badge NOT granted when any country needed more than 1 attempt")
        void anyCountryUsedMultipleAttempts_notPerfect() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(20L, "{\"trigger\":\"PERFECT_GAME\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(5, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 2));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("badge NOT granted when a country was not guessed at all")
        void unguessedCountry_notPerfect() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(20L, "{\"trigger\":\"PERFECT_GAME\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(5, 5));
            SessionRow session = sessionWithUnguessedCountry();
            engine.evaluateForUser(USER_ID, session);
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("previous perfect run is granted when a guest registers without a current session")
        void previousPerfectRun_grantedOnRegistration() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(20L, "{\"trigger\":\"PERFECT_GAME\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(new UserStatsRow(
                    0L, 1, 1, 1, 0, 0, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

            engine.evaluateForUser(USER_ID, null);

            verify(engineRepo).grantBadge(USER_ID, 20L, 0, 0);
        }
    }

    @Nested
    @DisplayName("trigger: GAMES_PLAYED threshold")
    class GamesPlayed {

        @Test
        @DisplayName("badge granted when games played meets threshold")
        void meetsThreshold_granted() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(30L, "{\"trigger\":\"GAMES_PLAYED\",\"threshold\":10}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(10, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo).grantBadge(USER_ID, 30L, 0, 0);
        }

        @Test
        @DisplayName("badge NOT granted when games played is below threshold")
        void belowThreshold_notGranted() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(30L, "{\"trigger\":\"GAMES_PLAYED\",\"threshold\":10}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(9, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("badge config edge cases")
    class BadgeConfigEdgeCases {

        @Test
        @DisplayName("badge with null grantConfig is silently skipped")
        void nullGrantConfig_skipped() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(99L, null)));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(5, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("badge with unknown trigger type is silently skipped")
        void unknownTrigger_skipped() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(99L, "{\"trigger\":\"MADE_UP_TRIGGER\",\"threshold\":1}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(stats(5, 5));
            engine.evaluateForUser(USER_ID, completedSession(true, 1));
            verify(engineRepo, never()).grantBadge(anyLong(), anyLong(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("BudgetGuessr triggers")
    class BudgetTriggers {

        @Test
        @DisplayName("large mastered-value thresholds are evaluated as long values")
        void masteredValueUsesLongThreshold() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(
                    40L,
                    "{\"trigger\":\"BUDGET_MASTERED_VALUE_USD\",\"threshold\":1000000000000}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(budgetStats(1_250_000_000_000L));

            engine.evaluateForUser(USER_ID, null);

            verify(engineRepo).grantBadge(USER_ID, 40L, 0, 0);
        }

        @Test
        @DisplayName("all-categories delegates to the authoritative project-progress query")
        void allCategoriesUsesProjectProgress() {
            when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(badge(
                    41L,
                    "{\"trigger\":\"BUDGET_ALL_CATEGORIES\",\"threshold\":\"ALL\"}")));
            when(engineRepo.loadUserStats(USER_ID)).thenReturn(budgetStats(0));
            when(engineRepo.hasMasteredAllBudgetCategories(USER_ID)).thenReturn(true);

            engine.evaluateForUser(USER_ID, null);

            verify(engineRepo).grantBadge(USER_ID, 41L, 0, 0);
        }
    }

    @Test
    void riverAttemptBadgeUsesAttemptedDistinctRiversNotOnlySolvedRivers() {
        when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(
                badge(50L, "{\"trigger\":\"RIVER_UNIQUE_ATTEMPTED\",\"threshold\":250}"),
                badge(51L, "{\"trigger\":\"RIVER_UNIQUE_SOLVED\",\"threshold\":50}")));
        when(engineRepo.loadUserStats(USER_ID)).thenReturn(new UserStatsRow(
                0L, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 10, 0, 0, 0, 0, 0, 0));

        engine.evaluateForUser(USER_ID, null);

        verify(engineRepo).grantBadge(USER_ID, 50L, 0, 0);
        verify(engineRepo, never()).grantBadge(USER_ID, 51L, 0, 0);
    }

    @Test
    @DisplayName("silhouette badges count unique countries and the globe badge needs the whole catalog")
    void silhouetteBadgesUseSolvedCountriesAndCatalogSize() {
        when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(
                badge(60L, "{\"trigger\":\"SILHOUETTE_UNIQUE_SOLVED\",\"threshold\":30}"),
                badge(61L, "{\"trigger\":\"SILHOUETTE_UNIQUE_SOLVED\",\"threshold\":100}"),
                badge(62L, "{\"trigger\":\"SILHOUETTE_GLOBE_COMPLETED\",\"threshold\":1}")));
        when(engineRepo.loadUserStats(USER_ID)).thenReturn(new UserStatsRow(
                0L, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 196, 197, 0, 0, 0, 0));

        engine.evaluateForUser(USER_ID, null);

        verify(engineRepo).grantBadge(USER_ID, 60L, 0, 0);
        verify(engineRepo).grantBadge(USER_ID, 61L, 0, 0);
        verify(engineRepo, never()).grantBadge(USER_ID, 62L, 0, 0);
    }

    @Test
    @DisplayName("flag badges count raised flags and the plaza badge needs every flag")
    void flagBadgesUseRaisedFlagsAndPlazaSize() {
        when(engineRepo.findUnclaimedTemplateBadges(USER_ID)).thenReturn(List.of(
                badge(70L, "{\"trigger\":\"FLAG_UNIQUE_SOLVED\",\"threshold\":30}"),
                badge(71L, "{\"trigger\":\"FLAG_UNIQUE_SOLVED\",\"threshold\":100}"),
                badge(72L, "{\"trigger\":\"FLAG_PLAZA_COMPLETED\",\"threshold\":1}")));
        when(engineRepo.loadUserStats(USER_ID)).thenReturn(new UserStatsRow(
                0L, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 197, 197, 0, 0));

        engine.evaluateForUser(USER_ID, null);

        verify(engineRepo).grantBadge(USER_ID, 70L, 0, 0);
        verify(engineRepo).grantBadge(USER_ID, 71L, 0, 0);
        verify(engineRepo).grantBadge(USER_ID, 72L, 0, 0);
    }

    private static TemplateBadgeRow badge(long id, String grantConfig) {
        return new TemplateBadgeRow(id, grantConfig);
    }

    private static UserStatsRow stats(int played, int won) {
        return new UserStatsRow(
                0L, 1, played, won, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static UserStatsRow budgetStats(long masteredValueUsd) {
        return new UserStatsRow(
                0L, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, masteredValueUsd, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Session where all countries are guessed with the given attemptsUsed count.
     */
    private static SessionRow completedSession(boolean guessed, int attemptsUsed) {
        SessionCountryRow c1 = new SessionCountryRow(
                1L, 42L, 1, 3, 0, attemptsUsed, guessed, 0L, 100, "DE", "Germany", "🇩🇪", "url", "Europe");
        SessionCountryRow c2 = new SessionCountryRow(
                2L, 43L, 2, 3, 0, attemptsUsed, guessed, 0L, 100, "FR", "France", "🇫🇷", "url", "Europe");
        return new SessionRow(
                1L, USER_ID, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.COMPLETED, 1,
                Instant.now(), Instant.now(), Instant.now(), 200, 3, 3, 0L, 1, null, null, "[]", List.of(c1, c2));
    }

    private static SessionRow sessionWithUnguessedCountry() {
        SessionCountryRow guessed = new SessionCountryRow(
                1L, 42L, 1, 3, 0, 1, true, 0L, 100, "DE", "Germany", "🇩🇪", "url", "Europe");
        SessionCountryRow failed = new SessionCountryRow(
                2L, 43L, 2, 3, 0, 3, false, 0L, 0, "FR", "France", "🇫🇷", "url", "Europe");
        return new SessionRow(
                1L, USER_ID, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.COMPLETED, 1,
                Instant.now(), Instant.now(), Instant.now(), 100, 3, 3, 0L, 1, null, null, "[]", List.of(guessed, failed));
    }
}
