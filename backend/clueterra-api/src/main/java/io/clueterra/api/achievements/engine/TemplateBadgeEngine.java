package io.clueterra.api.achievements.engine;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.common.enums.BadgeTrigger;
import io.clueterra.api.game.repository.SessionRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateBadgeEngine {

    private final BadgeEngineJdbcRepository engineRepo;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;

    public void evaluateForUser(Long userId, SessionRow session) {
        try {
            List<TemplateBadgeRow> unclaimed = engineRepo.findUnclaimedTemplateBadges(userId);
            if (unclaimed.isEmpty()) return;

            UserStatsRow stats = engineRepo.loadUserStats(userId);
            if (stats == null) return;
            int freezePerBadge = settingsService.getStreakFreezePerBadge();
            int freezeMax = settingsService.getStreakFreezeMax();

            for (TemplateBadgeRow badge : unclaimed) {
                if (evaluateBadge(badge, stats, session, userId)) {
                    engineRepo.grantBadge(userId, badge.id(), freezePerBadge, freezeMax);
                    log.info("Badge granted: userId={}, badgeId={}", userId, badge.id());
                }
            }
        } catch (Exception e) {
            log.error("Failed to evaluate template badges for userId={}", userId, e);
        }
    }

    private boolean evaluateBadge(TemplateBadgeRow badge, UserStatsRow stats, SessionRow session,
            Long userId) {
        if (badge.grantConfig() == null) return false;

        try {
            JsonNode config = objectMapper.readTree(badge.grantConfig());
            String triggerStr = config.path("trigger").asText();
            long threshold = config.path("threshold").asLong(1);

            BadgeTrigger trigger = BadgeTrigger.valueOf(triggerStr);
            return switch (trigger) {
                case LEVEL_REACHED -> stats.currentLevel() >= threshold;
                case STREAK_COUNT -> stats.currentStreak() >= threshold;
                case BEST_STREAK -> stats.bestStreak() >= threshold;
                case GAMES_PLAYED -> stats.challengesPlayed() >= threshold;
                case GAMES_WON -> stats.challengesWon() >= threshold;
                case TOTAL_XP -> stats.totalXp() >= threshold;
                case FIRST_WIN -> stats.challengesWon() >= 1;
                case PERFECT_GAME -> session == null
                        ? stats.perfectRuns() > 0
                        : isPerfectGame(session);
                case REGION_COMPLETED -> {
                    String region = config.path("threshold").asText();
                    if ("ALL".equals(region)) yield engineRepo.hasCompletedAllRegions(userId);
                    if (region.startsWith("sub:")) yield engineRepo.hasCompletedSubregion(userId, region.substring(4));
                    yield engineRepo.hasCompletedRegion(userId, region);
                }
                case BUDGET_CHALLENGES_COMPLETED -> stats.budgetCompletedChallenges() >= threshold;
                case BUDGET_EXACT_GUESSES -> stats.budgetExactGuesses() >= threshold;
                case BUDGET_CLEAN_SWEEPS -> stats.budgetCleanSweeps() >= threshold;
                case BUDGET_UNIQUE_PROJECTS -> stats.budgetUniqueProjects() >= threshold;
                case BUDGET_BEST_HIGHER_LOWER_STREAK -> stats.budgetBestHigherLowerStreak() >= threshold;
                case BUDGET_HIGHER_LOWER_CORRECT -> stats.budgetHigherLowerCorrect() >= threshold;
                case BUDGET_COST_PER_UNIT_CORRECT -> stats.budgetCostPerUnitCorrect() >= threshold;
                case BUDGET_ALL_CATEGORIES -> engineRepo.hasMasteredAllBudgetCategories(userId);
                case BUDGET_DISTINCT_COUNTRIES -> stats.budgetDistinctCountries() >= threshold;
                case BUDGET_MASTERED_VALUE_USD -> stats.budgetMasteredValueUsd() >= threshold;
                case BUDGET_SEVERE_MISSES -> stats.budgetSevereMisses() >= threshold;
                case RIVER_UNIQUE_ATTEMPTED -> stats.riverAttempted() >= threshold;
                case RIVER_UNIQUE_SOLVED -> stats.riverSolved() >= threshold;
                case SILHOUETTE_UNIQUE_SOLVED -> stats.silhouetteSolved() >= threshold;
                case SILHOUETTE_GLOBE_COMPLETED ->
                        stats.silhouetteTotal() > 0 && stats.silhouetteSolved() >= stats.silhouetteTotal();
                case FLAG_UNIQUE_SOLVED -> stats.flagSolved() >= threshold;
                case FLAG_PLAZA_COMPLETED -> stats.flagTotal() > 0 && stats.flagSolved() >= stats.flagTotal();
                case QUIZ_UNIQUE_EXPLORED -> stats.quizExplored() >= threshold;
                case QUIZ_TOUR_COMPLETED -> stats.quizTotal() > 0 && stats.quizExplored() >= stats.quizTotal();
            };
        } catch (Exception e) {
            log.warn("Failed to evaluate badge {}: {}", badge.id(), e.getMessage());
            return false;
        }
    }

    private boolean isPerfectGame(SessionRow session) {
        if (session == null || session.countries() == null || session.countries().isEmpty()) return false;
        return session.countries().stream()
                .allMatch(c -> c.isGuessed() && c.attemptsUsed() == 1);
    }
}
