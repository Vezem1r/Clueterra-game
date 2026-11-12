package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimResult;
import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimRow;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.budget.model.BudgetGuestClaimRow;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionRow;
import io.clueterra.api.flaggame.repository.FlagGameJdbcRepository;
import io.clueterra.api.quizgame.repository.QuizGameJdbcRepository;
import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;

@ExtendWith(MockitoExtension.class)
class GuestProgressClaimServiceTest {

    @Mock
    private AuthJdbcRepository authRepository;
    @Mock
    private BudgetGameSessionJdbcRepository budgetSessionRepository;
    @Mock
    private RiverGameJdbcRepository riverGameRepository;
    @Mock
    private SilhouetteGameJdbcRepository silhouetteGameRepository;
    @Mock
    private FlagGameJdbcRepository flagGameRepository;
    @Mock
    private QuizGameJdbcRepository quizGameRepository;
    @Mock
    private GameSessionJdbcRepository sessionRepository;
    @Mock
    private TemplateBadgeEngine templateBadgeEngine;
    @Mock
    private RedisHelper redisHelper;

    private GuestProgressClaimService service;

    @BeforeEach
    void setUp() {
        service = new GuestProgressClaimService(
                authRepository,
                budgetSessionRepository,
                riverGameRepository,
                silhouetteGameRepository,
                flagGameRepository,
                quizGameRepository,
                sessionRepository,
                templateBadgeEngine,
                redisHelper);
    }

    @Test
    @DisplayName("completed claim evaluates badges and evicts result caches")
    void completedClaimEvaluatesBadgesAndEvictsCaches() {
        SessionRow session = sessionRow();
        when(budgetSessionRepository.claimGuestProgress("guest-token", 42L))
                .thenReturn(new BudgetGuestClaimRow(
                        "NO_TODAY_PROGRESS", 100L, 42L, null, null));
        when(authRepository.claimGuestTodayProgress("guest-token", 42L))
                .thenReturn(new GuestProgressClaimRow(
                        "CLAIMED_COMPLETED", 100L, 42L, 99L, "COMPLETED"));
        when(sessionRepository.findById(99L)).thenReturn(Optional.of(session));

        GuestProgressClaimResult result = service.claimTodayProgress("guest-token", 42L);

        assertThat(result.status()).isEqualTo(GuestProgressClaimStatus.CLAIMED_COMPLETED);
        verify(templateBadgeEngine).evaluateForUser(42L, session);
        verify(redisHelper).delete("game:results:99");
        verify(redisHelper).delete("game:community-stats:EASY:2026-06-15");
    }

    @Test
    @DisplayName("in-progress claim only evicts stale session results cache")
    void inProgressClaimDoesNotEvaluateBadges() {
        when(budgetSessionRepository.claimGuestProgress("guest-token", 42L))
                .thenReturn(new BudgetGuestClaimRow(
                        "NO_TODAY_PROGRESS", 100L, 42L, null, null));
        when(authRepository.claimGuestTodayProgress("guest-token", 42L))
                .thenReturn(new GuestProgressClaimRow(
                        "CLAIMED_IN_PROGRESS", 100L, 42L, 99L, "IN_PROGRESS"));

        GuestProgressClaimResult result = service.claimTodayProgress("guest-token", 42L);

        assertThat(result.status()).isEqualTo(GuestProgressClaimStatus.CLAIMED_IN_PROGRESS);
        verify(templateBadgeEngine, never()).evaluateForUser(any(), any());
        verify(redisHelper).delete("game:results:99");
    }

    @Test
    @DisplayName("budget-only claim expires the transferred guest token and evaluates badges")
    void budgetOnlyClaimExpiresTransferredGuestToken() {
        when(budgetSessionRepository.claimGuestProgress("guest-token", 42L))
                .thenReturn(new BudgetGuestClaimRow(
                        "CLAIMED_IN_PROGRESS", 100L, 42L, 77L, "IN_PROGRESS"));
        when(authRepository.claimGuestTodayProgress("guest-token", 42L))
                .thenReturn(new GuestProgressClaimRow(
                        "NO_TODAY_PROGRESS", 100L, 42L, null, null));

        service.claimTodayProgress("guest-token", 42L);

        verify(authRepository).expireGuestToken(100L);
        verify(templateBadgeEngine).evaluateForUser(42L, null);
    }

    @Test
    @DisplayName("blank guest token does not call repository")
    void blankGuestTokenSkipsRepository() {
        GuestProgressClaimResult result = service.claimTodayProgress(" ", 42L);

        assertThat(result.status()).isEqualTo(GuestProgressClaimStatus.NO_GUEST);
        verify(authRepository, never()).claimGuestTodayProgress(" ", 42L);
    }

    private static SessionRow sessionRow() {
        Instant now = Instant.parse("2026-06-15T10:00:00Z");
        return new SessionRow(
                99L,
                42L,
                null,
                1L,
                LocalDate.of(2026, 6, 15),
                "EASY",
                SessionStatus.COMPLETED,
                0,
                now,
                now,
                now,
                100,
                3,
                3,
                0L,
                1,
                null,
                null,
                "[]",
                List.of());
    }
}
