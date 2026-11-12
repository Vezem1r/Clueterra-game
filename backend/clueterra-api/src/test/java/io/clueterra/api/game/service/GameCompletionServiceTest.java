package io.clueterra.api.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.game.repository.AwardResult;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameCompletionServiceTest {

    @Mock
    private GameSessionJdbcRepository sessionRepository;
    @Mock
    private SettingsService settingsService;
    @Mock
    private TemplateBadgeEngine templateBadgeEngine;

    @InjectMocks
    private GameCompletionService completionService;

    // All test cases use these multipliers to produce exact integer results.
    // hintsBonus  = (maxHints  - hintsRevealed)  / maxHints  * 0.5
    // attemptsBonus = (maxAttempts - attemptsUsed) / maxAttempts * 0.5
    private static final double HINTS_MULTIPLIER = 0.5;
    private static final double ATTEMPTS_MULTIPLIER = 0.5;
    private static final double TIME_MULTIPLIER = 0.5;
    private static final int TIME_THRESHOLD_SEC = 60;

    private static final AwardResult DEFAULT_AWARD = new AwardResult(1, 1, 0, 0, 80, 0, 1, 0);

    @BeforeEach
    void setupMultipliers() {
        when(settingsService.getHintsBonusMultiplier()).thenReturn(HINTS_MULTIPLIER);
        when(settingsService.getAttemptsBonusMultiplier()).thenReturn(ATTEMPTS_MULTIPLIER);
        when(settingsService.getTimeBonusMultiplier()).thenReturn(TIME_MULTIPLIER);
        when(settingsService.getTimeBonusThreshold()).thenReturn(TIME_THRESHOLD_SEC);
        when(settingsService.getBaseXp("EASY")).thenReturn(100);
        when(settingsService.getBaseXp("MEDIUM")).thenReturn(200);
        when(settingsService.getBaseXp("HARD")).thenReturn(400);
        when(settingsService.getLevelDivisor()).thenReturn(100);
        when(settingsService.getStreakMaxBonusPercent()).thenReturn(50);
        when(settingsService.getStreakMaxDays()).thenReturn(30);
        when(settingsService.getStreakCurvePower()).thenReturn(0.5);
        when(settingsService.getStreakFreezeStreakInterval()).thenReturn(7);
        when(settingsService.getStreakFreezeMax()).thenReturn(3);
        when(settingsService.getParticipationMultiplier()).thenReturn(0.5);
        when(sessionRepository.awardSessionXp(anyLong(), anyInt(), anyBoolean(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt()))
                .thenReturn(DEFAULT_AWARD);
    }

    @Nested
    @DisplayName("calculateXp")
    class CalculateXp {

        @Test
        @DisplayName("perfect game: 0 hints + 1 attempt -> full bonuses on top of base XP")
        void perfectGame_earnsFullBonus() {
            // hintsBonus   = 4/4 * 0.5 = 0.5
            // attemptsBonus = 3/4 * 0.5 = 0.375
            // xp = (long)(100 * 1.875) = 187
            int xp = completionService.calculateXp("EASY", 0, 1, 0L, 4, 4);
            assertThat(xp).isEqualTo(187);
        }

        @Test
        @DisplayName("worst game: all hints + all attempts -> base XP only")
        void worstGame_earnsBaseXpOnly() {
            // hintsBonus = 0, attemptsBonus = 0
            // xp = 100 * 1 = 100
            int xp = completionService.calculateXp("EASY", 4, 4, 0L, 4, 4);
            assertThat(xp).isEqualTo(100);
        }

        @Test
        @DisplayName("partial hints used reduces hints bonus proportionally")
        void partialHintsReducesBonus() {
            // 2 of 4 hints used -> hintsBonus = 2/4 * 0.5 = 0.25
            // all attempts used -> attemptsBonus = 0
            // xp = (long)(100 * 1.25) = 125
            int xp = completionService.calculateXp("EASY", 2, 4, 0L, 4, 4);
            assertThat(xp).isEqualTo(125);
        }

        @Test
        @DisplayName("time bonus applied when guessed faster than threshold")
        void timeBonusAppliedBelowThreshold() {
            // 30 s < 60 s threshold -> timeBonus = (60000-30000)/60000 * 0.5 = 0.25
            // worst game + time bonus: xp = (long)(100 * 1.25) = 125
            int xp = completionService.calculateXp("EASY", 4, 4, 30_000L, 4, 4);
            assertThat(xp).isEqualTo(125);
        }

        @Test
        @DisplayName("time bonus is zero when guessed exactly at threshold")
        void timeBonusZeroAtThreshold() {
            int xp = completionService.calculateXp("EASY", 4, 4, 60_000L, 4, 4);
            assertThat(xp).isEqualTo(100); // no bonus
        }

        @Test
        @DisplayName("time bonus is zero when guessed slower than threshold")
        void timeBonusZeroAboveThreshold() {
            int xp = completionService.calculateXp("EASY", 4, 4, 120_000L, 4, 4);
            assertThat(xp).isEqualTo(100);
        }

        @Test
        @DisplayName("time bonus NOT applied when timeSpentMs = 0 (unknown)")
        void timeBonusSkippedWhenZero() {
            int withKnownTime = completionService.calculateXp("EASY", 4, 4, 30_000L, 4, 4);
            int withUnknownTime = completionService.calculateXp("EASY", 4, 4, 0L, 4, 4);
            assertThat(withKnownTime).isGreaterThan(withUnknownTime);
        }

        @Test
        @DisplayName("HARD level earns 4× more base XP than EASY with identical performance")
        void hardLevelEarnsMoreThanEasy() {
            int easy = completionService.calculateXp("EASY", 4, 4, 0L, 4, 4);
            int hard = completionService.calculateXp("HARD", 4, 4, 0L, 4, 4);
            assertThat(hard).isEqualTo(easy * 4);
        }

        @Test
        @DisplayName("XP is capped at Integer.MAX_VALUE and never overflows")
        void xpCappedAtIntegerMaxValue() {
            when(settingsService.getHintsBonusMultiplier()).thenReturn(1_000_000.0);
            when(settingsService.getAttemptsBonusMultiplier()).thenReturn(1_000_000.0);
            when(settingsService.getBaseXp("HARD")).thenReturn(2_000_000);

            int xp = completionService.calculateXp("HARD", 0, 1, 0L, 4, 4);

            assertThat(xp).isEqualTo(Integer.MAX_VALUE).isPositive();
        }

        @Test
        @DisplayName("zero challenge limits are clamped before XP calculation")
        void zeroLimitsAreClamped() {
            int xp = completionService.calculateXp("EASY", 0, 1, 0L, 0, 0);

            assertThat(xp).isEqualTo(150);
        }
    }

    @Nested
    @DisplayName("completeSession")
    class CompleteSession {

        @Test
        @DisplayName("totalXp is the sum of xpEarned across all session countries")
        void totalXpSumsCountryXp() {
            AwardResult result = new AwardResult(1, 1, 5.0, 4, 80, 0, 1, 0);
            when(sessionRepository.awardSessionXp(eq(42L), eq(80), anyBoolean(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt()))
                    .thenReturn(result);
            SessionRow session = buildSession(42L, List.of(
                    country(50, true),   // guessed
                    country(30, false)   // not guessed
            ));

            completionService.completeSession(session);
            verify(sessionRepository).completeSession(session.id(), 80, 5.0, 4);
        }

        @Test
        @DisplayName("when all countries earned 0 XP, falls back to participationXp")
        void zeroXpFallsBackToParticipation() {
            AwardResult result = new AwardResult(1, 1, 0, 0, 50, 0, 1, 0);
            when(sessionRepository.awardSessionXp(eq(42L), eq(50), anyBoolean(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt()))
                    .thenReturn(result);
            SessionRow session = buildSession(42L, List.of(country(0, false)));
            completionService.completeSession(session);
            verify(sessionRepository).completeSession(session.id(), 50, 0.0, 0);
        }

        @Test
        @DisplayName("awardSessionXp and badge evaluation called for logged-in user")
        void userSession_awardsXpAndEvaluatesBadges() {
            SessionRow session = buildSession(42L, List.of(country(80, true)));
            completionService.completeSession(session);
            verify(sessionRepository).awardSessionXp(eq(42L), eq(80), eq(true), eq(100), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt());
            verify(sessionRepository).applyCompletedSessionProjection(session.id());
            verify(templateBadgeEngine).evaluateForUser(42L, session);
        }

        @Test
        @DisplayName("projection is applied after completion and before badge evaluation")
        void userSession_appliesProjectionBeforeEvaluatingBadges() {
            SessionRow session = buildSession(42L, List.of(country(80, true)));

            completionService.completeSession(session);

            var inOrder = inOrder(sessionRepository, templateBadgeEngine);
            inOrder.verify(sessionRepository).completeSession(session.id(), 80, 0.0, 0);
            inOrder.verify(sessionRepository).applyCompletedSessionProjection(session.id());
            inOrder.verify(templateBadgeEngine).evaluateForUser(42L, session);
        }

        @Test
        @DisplayName("awardSessionXp and badge evaluation are SKIPPED for guest sessions (no userId)")
        void guestSession_skipsXpAndBadges() {
            SessionRow session = buildSession(null, List.of(country(80, true)));
            completionService.completeSession(session);
            verify(sessionRepository, never()).awardSessionXp(anyLong(), anyInt(), anyBoolean(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt());
            verify(sessionRepository, never()).applyCompletedSessionProjection(anyLong());
            verify(templateBadgeEngine, never()).evaluateForUser(any(), any());
        }

        @Test
        @DisplayName("won flag is true when at least one country was guessed correctly")
        void wonTrueWhenAnyCountryGuessed() {
            SessionRow session = buildSession(42L, List.of(country(0, false), country(50, true)));
            completionService.completeSession(session);
            verify(sessionRepository).awardSessionXp(eq(42L), anyInt(), eq(true), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("won flag is false when no country was guessed correctly")
        void wonFalseWhenNothingGuessed() {
            SessionRow session = buildSession(42L, List.of(country(0, false), country(0, false)));
            completionService.completeSession(session);
            verify(sessionRepository).awardSessionXp(eq(42L), anyInt(), eq(false), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), anyInt(), anyInt());
        }
    }

    private static SessionRow buildSession(Long userId, List<SessionCountryRow> countries) {
        return new SessionRow(
                1L, userId, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.IN_PROGRESS, 0,
                Instant.now(), Instant.now(), null, 0, 4, 4, 0L, 1, null, null, "[]", countries);
    }

    private static SessionCountryRow country(int xpEarned, boolean guessed) {
        return new SessionCountryRow(
                1L, 42L, 1, 4, 0, 0, guessed, 0L, xpEarned, "DE", "Germany", "🇩🇪", "url", "Europe");
    }
}
