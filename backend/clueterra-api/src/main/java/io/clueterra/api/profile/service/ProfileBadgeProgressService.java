package io.clueterra.api.profile.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.common.enums.BadgeTrigger;
import io.clueterra.api.profile.dto.ProfileBadgeDto;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeCountryProgressRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeBudgetCategoryProgressRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeGrantConfigRow;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressDto;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressItemDto;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressKind;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressScope;
import io.clueterra.api.profile.dto.badgeProgress.ProfileBadgeProgressStatsRow;
import io.clueterra.api.profile.repository.ProfileBadgeProgressJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileBadgeProgressService {

    private static final int MISSING_COUNTRY_PREVIEW_LIMIT = 2;

    private final ProfileBadgeProgressJdbcRepository progressRepository;
    private final ObjectMapper objectMapper;

    public List<ProfileBadgeDto> attachProgress(Long userId, List<ProfileBadgeDto> badges) {
        if (badges == null || badges.isEmpty()) {
            return List.of();
        }

        Map<Long, ProfileBadgeGrantConfigRow> configsByBadgeId = progressRepository.findGrantConfigs(
                        badges.stream().map(ProfileBadgeDto::id).toList())
                .stream()
                .collect(Collectors.toMap(
                        ProfileBadgeGrantConfigRow::id,
                        Function.identity(),
                        (first, ignored) -> first));

        ProfileBadgeProgressStatsRow stats = progressRepository.loadUserStats(userId);
        if (stats == null) {
            return badges;
        }

        return badges.stream()
                .map(badge -> withProgress(badge, resolveProgress(userId, configsByBadgeId.get(badge.id()), stats)))
                .toList();
    }

    private ProfileBadgeDto withProgress(ProfileBadgeDto badge, ProfileBadgeProgressDto progress) {
        return new ProfileBadgeDto(
                badge.id(),
                badge.slug(),
                badge.imageUrl(),
                badge.accentColorKey(),
                badge.effectColorKey(),
                badge.name(),
                badge.description(),
                badge.unlocked(),
                badge.grantedAt(),
                progress);
    }

    private ProfileBadgeProgressDto resolveProgress(
            Long userId,
            ProfileBadgeGrantConfigRow configRow,
            ProfileBadgeProgressStatsRow stats) {
        if (configRow == null || !"TEMPLATE".equals(configRow.grantType()) || configRow.grantConfig() == null) {
            return null;
        }

        try {
            JsonNode config = objectMapper.readTree(configRow.grantConfig());
            String triggerValue = config.path("trigger").asText(null);
            if (triggerValue == null || triggerValue.isBlank()) {
                return null;
            }

            BadgeTrigger trigger = BadgeTrigger.valueOf(triggerValue);
            long threshold = config.path("threshold").asLong(1);
            return switch (trigger) {
                case LEVEL_REACHED -> counterProgress(stats.currentLevel(), threshold);
                case STREAK_COUNT -> counterProgress(stats.currentStreak(), threshold);
                case BEST_STREAK -> counterProgress(stats.bestStreak(), threshold);
                case GAMES_PLAYED -> counterProgress(stats.challengesPlayed(), threshold);
                case GAMES_WON -> counterProgress(stats.challengesWon(), threshold);
                case TOTAL_XP -> counterProgress(stats.totalXp(), threshold);
                case FIRST_WIN -> counterProgress(Math.min(stats.challengesWon(), 1), 1);
                case PERFECT_GAME -> counterProgress(stats.perfectRuns(), 1);
                case REGION_COMPLETED -> countryCollectionProgress(userId, config.path("threshold").asText(null));
                case BUDGET_CHALLENGES_COMPLETED ->
                        counterProgress(stats.budgetCompletedChallenges(), threshold);
                case BUDGET_EXACT_GUESSES -> counterProgress(stats.budgetExactGuesses(), threshold);
                case BUDGET_CLEAN_SWEEPS -> counterProgress(stats.budgetCleanSweeps(), threshold);
                case BUDGET_UNIQUE_PROJECTS -> counterProgress(stats.budgetUniqueProjects(), threshold);
                case BUDGET_BEST_HIGHER_LOWER_STREAK ->
                        counterProgress(stats.budgetBestHigherLowerStreak(), threshold);
                case BUDGET_HIGHER_LOWER_CORRECT ->
                        counterProgress(stats.budgetHigherLowerCorrect(), threshold);
                case BUDGET_COST_PER_UNIT_CORRECT ->
                        counterProgress(stats.budgetCostPerUnitCorrect(), threshold);
                case BUDGET_ALL_CATEGORIES -> budgetCategoryProgress(userId);
                case BUDGET_DISTINCT_COUNTRIES ->
                        counterProgress(stats.budgetDistinctCountries(), threshold);
                case BUDGET_MASTERED_VALUE_USD ->
                        currencyProgress(stats.budgetMasteredValueUsd(), threshold);
                case BUDGET_SEVERE_MISSES -> counterProgress(stats.budgetSevereMisses(), threshold);
                case RIVER_UNIQUE_ATTEMPTED -> counterProgress(stats.riverAttempted(), threshold);
                case RIVER_UNIQUE_SOLVED -> counterProgress(stats.riverSolved(), threshold);
                case SILHOUETTE_UNIQUE_SOLVED -> counterProgress(stats.silhouetteSolved(), threshold);
                case SILHOUETTE_GLOBE_COMPLETED -> counterProgress(stats.silhouetteSolved(), stats.silhouetteTotal());
                case FLAG_UNIQUE_SOLVED -> counterProgress(stats.flagSolved(), threshold);
                case FLAG_PLAZA_COMPLETED -> counterProgress(stats.flagSolved(), stats.flagTotal());
                case QUIZ_UNIQUE_EXPLORED -> counterProgress(stats.quizExplored(), threshold);
                case QUIZ_TOUR_COMPLETED -> counterProgress(stats.quizExplored(), stats.quizTotal());
            };
        } catch (Exception e) {
            log.warn("Failed to resolve badge progress for badgeId={}: {}", configRow.id(), e.getMessage());
            return null;
        }
    }

    private ProfileBadgeProgressDto budgetCategoryProgress(Long userId) {
        ProfileBadgeBudgetCategoryProgressRow progress = progressRepository.loadBudgetCategoryProgress(userId);
        if (progress.totalCount() <= 0) {
            return null;
        }
        return counterProgress(progress.masteredCount(), progress.totalCount());
    }

    private ProfileBadgeProgressDto countryCollectionProgress(Long userId, String threshold) {
        ProfileBadgeProgressScope scope = resolveCountryScope(threshold);
        if (scope == null) {
            return null;
        }

        ProfileBadgeCountryProgressRow progress =
                progressRepository.loadCountryProgress(userId, scope.type(), scope.value());
        if (progress.totalCount() <= 0) {
            return null;
        }

        long remaining = Math.max(0, (long) progress.totalCount() - progress.completedCount());
        List<ProfileBadgeProgressItemDto> missingItems = remaining == 0
                ? List.of()
                : progressRepository.findMissingCountries(
                                userId,
                                scope.type(),
                                scope.value(),
                                MISSING_COUNTRY_PREVIEW_LIMIT)
                        .stream()
                        .map(country -> new ProfileBadgeProgressItemDto(
                                country.code(),
                                country.name(),
                                country.flag(),
                                country.flagUrl()))
                        .toList();

        return new ProfileBadgeProgressDto(
                ProfileBadgeProgressKind.COUNTRY_COLLECTION,
                progress.completedCount(),
                progress.totalCount(),
                remaining,
                progressPercent(progress.completedCount(), progress.totalCount()),
                formatCompact(progress.completedCount()),
                formatCompact(progress.totalCount()),
                formatCompact(remaining),
                missingItems,
                (int) Math.max(0, remaining - missingItems.size()),
                formatCompact(Math.max(0, remaining - missingItems.size())));
    }

    private ProfileBadgeProgressScope resolveCountryScope(String threshold) {
        if (threshold == null || threshold.isBlank()) {
            return null;
        }
        if ("ALL".equals(threshold)) {
            return new ProfileBadgeProgressScope("ALL", null);
        }
        if (threshold.startsWith("sub:")) {
            return new ProfileBadgeProgressScope("SUBREGION", threshold.substring(4));
        }
        return new ProfileBadgeProgressScope("REGION", threshold);
    }

    private ProfileBadgeProgressDto counterProgress(long current, long target) {
        long safeTarget = Math.max(1, target);
        long remaining = Math.max(0, safeTarget - current);
        return new ProfileBadgeProgressDto(
                ProfileBadgeProgressKind.COUNTER,
                current,
                safeTarget,
                remaining,
                progressPercent(current, safeTarget),
                formatCompact(current),
                formatCompact(safeTarget),
                formatCompact(remaining),
                List.of(),
                0,
                "0");
    }

    private ProfileBadgeProgressDto currencyProgress(long current, long target) {
        ProfileBadgeProgressDto progress = counterProgress(current, target);
        return new ProfileBadgeProgressDto(
                progress.kind(),
                progress.current(),
                progress.target(),
                progress.remaining(),
                progress.percent(),
                "$" + formatCompact(progress.current()),
                "$" + formatCompact(progress.target()),
                "$" + formatCompact(progress.remaining()),
                progress.missingItems(),
                progress.hiddenMissingCount(),
                progress.hiddenMissingCountDisplay());
    }

    private int progressPercent(long current, long target) {
        if (target <= 0) {
            return 0;
        }
        double clampedCurrent = Math.max(0, Math.min(current, target));
        return (int) Math.round((clampedCurrent / target) * 100);
    }

    private String formatCompact(long value) {
        long absolute = Math.abs(value);
        if (absolute >= 1_000_000_000_000L) {
            return formatScaled(value, 1_000_000_000_000L, "T");
        }
        if (absolute >= 1_000_000_000L) {
            return formatScaled(value, 1_000_000_000L, "B");
        }
        if (absolute >= 1_000_000L) {
            return formatScaled(value, 1_000_000L, "M");
        }
        if (absolute >= 10_000L) {
            return formatScaled(value, 1_000L, "K");
        }
        return String.format(Locale.US, "%,d", value);
    }

    private String formatScaled(long value, long divisor, String suffix) {
        double scaled = (double) value / divisor;
        String pattern = Math.abs(scaled) >= 10 ? "%.0f%s" : "%.1f%s";
        return String.format(Locale.US, pattern, scaled, suffix).replace(".0" + suffix, suffix);
    }
}
