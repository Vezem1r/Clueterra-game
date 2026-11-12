package io.clueterra.api.game.service;

import org.springframework.stereotype.Service;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.game.repository.AwardResult;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameCompletionService {

    private final GameSessionJdbcRepository sessionRepository;
    private final SettingsService settingsService;
    private final TemplateBadgeEngine templateBadgeEngine;

    public int calculateXp(String challengeLevel, int hintsRevealed, int attemptsUsed, long timeSpentMs, int maxHints, int maxAttempts) {
        int baseXp = settingsService.getBaseXp(challengeLevel);
        int safeMaxHints = Math.max(1, maxHints);
        int safeMaxAttempts = Math.max(1, maxAttempts);
        int safeHintsRevealed = Math.max(0, Math.min(hintsRevealed, safeMaxHints));
        int safeAttemptsUsed = Math.max(0, Math.min(attemptsUsed, safeMaxAttempts));

        double hintsBonus = (double) (safeMaxHints - safeHintsRevealed) / safeMaxHints * settingsService.getHintsBonusMultiplier();
        double attemptsBonus = (double) (safeMaxAttempts - safeAttemptsUsed) / safeMaxAttempts * settingsService.getAttemptsBonusMultiplier();
        double timeBonus = 0;
        if (timeSpentMs > 0) {
            long thresholdMs = Math.max(1L, settingsService.getTimeBonusThreshold() * 1000L);
            timeBonus = Math.max(0, (double) (thresholdMs - timeSpentMs) / thresholdMs) * settingsService.getTimeBonusMultiplier();
        }
        long xp = (long) (baseXp * (1 + hintsBonus + attemptsBonus + timeBonus));
        return (int) Math.min(xp, Integer.MAX_VALUE);
    }

    public void completeSession(SessionRow session) {
        int totalXp = session.countries().stream().mapToInt(SessionCountryRow::xpEarned).sum();

        int participationXp = (int) (settingsService.getBaseXp(session.challengeLevel()) * settingsService.getParticipationMultiplier());
        if (totalXp == 0) {
            totalXp = participationXp;
        }

        log.info("Session finalized: sessionId={}, userId={}, baseXp={}", session.id(), session.userId(), totalXp);

        if (session.userId() != null) {
            AwardResult result = awardCompletedSessionToUser(session.userId(), session, totalXp);
            int finalXp = result.totalXpAwarded();
            double bonusPct = result.streakBonusPercent();
            int bonusXp = result.streakBonusXp();
            sessionRepository.completeSession(session.id(), finalXp, bonusPct, bonusXp);
            sessionRepository.recordUserXpEvent(session.userId(), session.id(), finalXp, "DAILY_CHALLENGE", session.challengeId(), session.challengeLevel());
            sessionRepository.applyCompletedSessionProjection(session.id());
            templateBadgeEngine.evaluateForUser(session.userId(), session);
            return;
        }

        sessionRepository.completeSession(session.id(), totalXp, 0, 0);
    }

    private AwardResult awardCompletedSessionToUser(Long userId, SessionRow session, int totalXp) {
        boolean won = session.countries().stream().anyMatch(SessionCountryRow::isGuessed);
        AwardResult result = sessionRepository.awardSessionXp(
                userId, totalXp, won,
                settingsService.getLevelDivisor(),
                settingsService.getStreakMaxBonusPercent(),
                settingsService.getStreakMaxDays(),
                settingsService.getStreakCurvePower(),
                settingsService.getStreakFreezeStreakInterval(),
                settingsService.getStreakFreezeMax());
        log.info("XP awarded: userId={}, totalXp={}, streakBonus={}%, bonusXp={}, newStreak={}",
                userId, result.totalXpAwarded(), result.streakBonusPercent(), result.streakBonusXp(), result.newStreak());
        return result;
    }
}
