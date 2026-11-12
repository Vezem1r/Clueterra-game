package io.clueterra.api.auth.service;

import java.time.LocalDate;

import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimResult;
import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimRow;
import io.clueterra.api.budget.model.BudgetGuestClaimRow;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionRow;
import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.flaggame.repository.FlagGameJdbcRepository;
import io.clueterra.api.quizgame.repository.QuizGameJdbcRepository;
import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestProgressClaimService {

    private static final String RESULTS_CACHE_PREFIX = "game:results:";
    private static final String COMMUNITY_STATS_CACHE_PREFIX = "game:community-stats:";

    private final AuthJdbcRepository authRepository;
    private final BudgetGameSessionJdbcRepository budgetSessionRepository;
    private final RiverGameJdbcRepository riverGameRepository;
    private final SilhouetteGameJdbcRepository silhouetteGameRepository;
    private final FlagGameJdbcRepository flagGameRepository;
    private final QuizGameJdbcRepository quizGameRepository;
    private final GameSessionJdbcRepository sessionRepository;
    private final TemplateBadgeEngine templateBadgeEngine;
    private final RedisHelper redisHelper;

    @Transactional
    public GuestProgressClaimResult claimTodayProgress(String guestToken, Long targetUserId) {
        if (!hasText(guestToken) || targetUserId == null) {
            return new GuestProgressClaimResult(
                    GuestProgressClaimStatus.NO_GUEST, null, targetUserId, null, null);
        }

        boolean riverClaimed = riverGameRepository.claimGuest(guestToken, targetUserId);
        log.info("River game guest progress claim result: claimed={}, targetUserId={}", riverClaimed, targetUserId);

        boolean silhouetteClaimed = silhouetteGameRepository.claimGuest(guestToken, targetUserId);
        log.info("Silhouette game guest progress claim result: claimed={}, targetUserId={}", silhouetteClaimed, targetUserId);

        boolean flagClaimed = flagGameRepository.claimGuest(guestToken, targetUserId);
        log.info("Flag game guest progress claim result: claimed={}, targetUserId={}", flagClaimed, targetUserId);

        boolean quizClaimed = quizGameRepository.claimGuest(guestToken, targetUserId);
        log.info("World quiz guest progress claim result: claimed={}, targetUserId={}", quizClaimed, targetUserId);

        BudgetGuestClaimRow budgetClaim = budgetSessionRepository.claimGuestProgress(guestToken, targetUserId);
        log.info("BudgetGuessr guest progress claim result: status={}, guestUserId={}, targetUserId={}, sessionId={}",
                budgetClaim.status(), budgetClaim.guestUserId(), budgetClaim.targetUserId(), budgetClaim.sessionId());

        GuestProgressClaimRow row = authRepository.claimGuestTodayProgress(guestToken, targetUserId);
        GuestProgressClaimStatus status = GuestProgressClaimStatus.valueOf(row.claimStatus());
        GuestProgressClaimResult result = new GuestProgressClaimResult(
                status,
                row.guestUserId(),
                row.targetUserId(),
                row.sessionId(),
                row.sessionStatus());

        if (budgetClaim.status().startsWith("CLAIMED_") && budgetClaim.guestUserId() != null) {
            authRepository.expireGuestToken(budgetClaim.guestUserId());
            templateBadgeEngine.evaluateForUser(targetUserId, null);
        }

        if (status == GuestProgressClaimStatus.CLAIMED_COMPLETED && row.sessionId() != null) {
            sessionRepository.findById(row.sessionId()).ifPresent(session -> {
                boolean reassigned = sessionRepository.reassignSessionXpEvent(session.id(), targetUserId, session.totalXpEarned());
                if (!reassigned) {
                    sessionRepository.recordUserXpEvent(
                            targetUserId,
                            session.id(),
                            session.totalXpEarned(),
                            "DAILY_CHALLENGE",
                            session.challengeId(),
                            session.challengeLevel());
                }
                templateBadgeEngine.evaluateForUser(targetUserId, session);
                evictResultCaches(session);
            });
        } else if (status == GuestProgressClaimStatus.CLAIMED_IN_PROGRESS && row.sessionId() != null) {
            redisHelper.delete(RESULTS_CACHE_PREFIX + row.sessionId());
        }

        if (riverClaimed) {
            authRepository.findByGuestToken(guestToken).ifPresent(guest -> authRepository.expireGuestToken(guest.id()));
            templateBadgeEngine.evaluateForUser(targetUserId, null);
        }

        log.info(
                "Guest progress claim result: status={}, guestUserId={}, targetUserId={}, sessionId={}",
                result.status(), result.guestUserId(), result.targetUserId(), result.sessionId());
        return result;
    }

    @Transactional
    public void finalizeConvertedBudgetProgress(long userId) {
        boolean xpApplied = budgetSessionRepository.finalizeConvertedGuestProgress(userId);
        templateBadgeEngine.evaluateForUser(userId, null);
        if (xpApplied) {
            log.info("Applied completed BudgetGuessr guest XP after registration: userId={}", userId);
        }
    }

    private void evictResultCaches(SessionRow session) {
        redisHelper.delete(RESULTS_CACHE_PREFIX + session.id());
        LocalDate challengeDate = session.challengeDate();
        if (challengeDate != null) {
            redisHelper.delete(COMMUNITY_STATS_CACHE_PREFIX + session.challengeLevel() + ":" + challengeDate);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
