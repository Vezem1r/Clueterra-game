package io.clueterra.api.profile.service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.assets.service.BadgeAssetService;
import io.clueterra.api.exception.ProfileLockedException;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.profile.dto.ProfileBadgeDto;
import io.clueterra.api.profile.dto.ProfileBadgePageResponse;
import io.clueterra.api.profile.dto.BudgetProfileStatsDto;
import io.clueterra.api.profile.dto.ProfileDifficultyStatsDto;
import io.clueterra.api.profile.dto.ProfileDto;
import io.clueterra.api.profile.dto.UnacknowledgedBadgeDto;
import io.clueterra.api.profile.dto.UpdateProfileRequest;
import io.clueterra.api.profile.repository.ProfileJdbcRepository;
import io.clueterra.api.profile.repository.ProfileRow;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private static final List<String> DIFFICULTY_ORDER = List.of("EASY", "MEDIUM", "HARD");
    private static final int MIN_RECOMMENDATION_RUNS = 3;
    private static final double INCREASE_RECOMMENDATION_SOLVE_RATE = 85.0;
    private static final int MAX_BADGE_PAGE_SIZE = 50;

    private final ProfileJdbcRepository profileRepository;
    private final StorageService storageService;
    private final SettingsService settingsService;
    private final AvatarImageService avatarImageService;
    private final ProfileBadgeProgressService badgeProgressService;
    private final BadgeAssetService badgeAssetService;
    private final Clock clock;

    @Transactional
    public ProfileDto getOwnProfile(Long userId) {
        ProfileRow row = profileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDto(row, true, userId);
    }

    @Transactional(readOnly = true)
    public ProfileDto getPublicProfile(String username) {
        ProfileRow row = profileRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDto(row, false, null);
    }

    @Transactional
    public ProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        ProfileRow current = profileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (current.profileLocked()) {
            throw new ProfileLockedException();
        }
        String avatarUrl = normalizeOwnedAvatarUrl(request.avatarUrl());
        ProfileRow updated = profileRepository.upsertProfile(
                        userId,
                        request.displayName(),
                        avatarUrl,
                        request.socialDiscoverable())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        log.info("Profile updated: userId={}", userId);
        return toDto(updated, true, userId);
    }

    @Transactional
    public ProfileDto uploadAvatar(Long userId, MultipartFile file) {
        ProfileRow current = profileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (current.profileLocked()) {
            throw new ProfileLockedException();
        }

        String previousAvatarUrl = current.avatarUrl();

        ProcessedAvatar avatar = avatarImageService.process(file);
        String url = storageService.upload(
                userId,
                new ByteArrayInputStream(avatar.data()),
                avatar.data().length,
                avatar.contentType(),
                avatar.extension());

        try {
            profileRepository.updateAvatarUrl(userId, url);
        } catch (RuntimeException e) {
            if (storageService.isMinioUrl(url)) {
                storageService.delete(url);
            }
            throw e;
        }

        if (previousAvatarUrl != null
                && storageService.isMinioUrl(previousAvatarUrl)
                && !previousAvatarUrl.equals(url)) {
            storageService.delete(previousAvatarUrl);
        }

        log.info("Avatar uploaded: userId={}", userId);
        return profileRepository.findOwnProfile(userId)
                .map(row -> toDto(row, true, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public ProfileDto deleteAvatar(Long userId) {
        ProfileRow current = profileRepository.findOwnProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (current.avatarUrl() != null && storageService.isMinioUrl(current.avatarUrl())) {
            storageService.delete(current.avatarUrl());
        }

        profileRepository.updateAvatarUrl(userId, null);
        log.info("Avatar deleted: userId={}", userId);
        return profileRepository.findOwnProfile(userId)
                .map(row -> toDto(row, true, userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public ProfileBadgePageResponse getBadgesPaginated(Long userId, String locale, String filter, int page, int size) {
        validateBadgePagination(page, size);
        List<ProfileBadgeDto> badges = badgeProgressService.attachProgress(userId, resolveBadgeImageUrls(
                profileRepository.findBadgesPaginated(userId, locale, filter, page, size)));
        int total = profileRepository.countBadgesFiltered(userId, filter);
        int totalPages = totalPages(total, size);
        int earnedCount = profileRepository.countBadgesFiltered(userId, "earned");
        int lockedVisibleCount = profileRepository.countBadgesFiltered(userId, "locked");
        int hiddenSecretCount = profileRepository.countHiddenSecrets(userId);
        return new ProfileBadgePageResponse(
                badges,
                total,
                totalPages,
                page,
                size,
                earnedCount,
                lockedVisibleCount,
                hiddenSecretCount);
    }

    @Transactional(readOnly = true)
    public ProfileBadgePageResponse getPublicBadgesPaginated(String username, String locale, int page, int size) {
        validateBadgePagination(page, size);
        List<ProfileBadgeDto> badges = resolveBadgeImageUrls(
                profileRepository.findPublicBadgesPaginated(username, locale, page, size));
        int total = profileRepository.countPublicBadges(username);
        int totalPages = totalPages(total, size);
        return new ProfileBadgePageResponse(
                badges,
                total,
                totalPages,
                page,
                size,
                total,
                0,
                0);
    }

    private static final int BADGE_SAMPLE_LIMIT = 3;

    private static void validateBadgePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_BADGE_PAGE_SIZE) {
            throw new IllegalArgumentException("Size must be between 1 and " + MAX_BADGE_PAGE_SIZE);
        }
    }

    private static int totalPages(int total, int size) {
        return (int) (((long) total + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public List<ProfileBadgeDto> getRandomBadgeSamples(String locale) {
        return resolveBadgeImageUrls(profileRepository.findRandomBadgeSamples(locale, BADGE_SAMPLE_LIMIT));
    }

    public List<UnacknowledgedBadgeDto> getUnacknowledged(Long userId, String locale) {
        return profileRepository.findUnacknowledged(userId, locale).stream()
                .map(this::resolveBadgeImageUrl)
                .toList();
    }

    public void acknowledgeBadges(Long userId, List<String> badgeIds) {
        if (badgeIds != null && !badgeIds.isEmpty()) {
            List<Long> ids = badgeIds.stream().map(Long::parseLong).toList();
            profileRepository.acknowledgeBadges(userId, ids);
        }
    }

    @Transactional
    public void acknowledgeStreakFreezeNotice(Long userId) {
        profileRepository.acknowledgeStreakFreezeNotice(userId);
    }

    private ProfileDto toDto(ProfileRow row, boolean isOwnProfile, Long userId) {
        int divisor = settingsService.getLevelDivisor();
        long xpForNext = (long) row.currentLevel() * row.currentLevel() * divisor;
        long xpForCurrent = (long) (row.currentLevel() - 1) * (row.currentLevel() - 1) * divisor;
        if (row.totalXp() < xpForCurrent) {
            xpForCurrent = 0;
        }

        double streakBonusPercent = 0;
        Integer previousFreezeAtStreak = null;
        Integer nextFreezeAtStreak = null;
        String resetAtUtc = null;
        int freezesAvailable;

        int maxBonus = settingsService.getStreakMaxBonusPercent();
        int maxDays = settingsService.getStreakMaxDays();
        double curvePower = settingsService.getStreakCurvePower();
        int freezeInterval = settingsService.getStreakFreezeStreakInterval();
        int freezeMax = Math.max(0, settingsService.getStreakFreezeMax());

        if (row.currentStreak() > 0 && maxBonus > 0 && maxDays > 0) {
            streakBonusPercent = Math.round(maxBonus * Math.pow(
                    (double) Math.min(row.currentStreak(), maxDays) / maxDays,
                    curvePower) * 10.0) / 10.0;
        }
        freezesAvailable = row.streakFreezes();
        if (freezeInterval > 0 && freezeMax > 0 && freezesAvailable < freezeMax) {
            previousFreezeAtStreak = (row.currentStreak() / freezeInterval) * freezeInterval;
            nextFreezeAtStreak = previousFreezeAtStreak + freezeInterval;
        }

        if (isOwnProfile) {
            resetAtUtc = computeResetAtUtc();
        }

        List<ProfileDifficultyStatsDto> difficultyStats = isOwnProfile && userId != null
                ? profileRepository.findDifficultyStats(userId)
                : List.of();
        String difficultyRecommendation = isOwnProfile
                ? resolveDifficultyRecommendation(difficultyStats)
                : null;
        Boolean freezeNoticeAcknowledged = isOwnProfile
                ? row.streakFreezesAcknowledgedSinceCompletion() >= row.streakFreezesUsedSinceCompletion()
                : null;

        return new ProfileDto(
                row.username(),
                isOwnProfile ? row.email() : null,
                row.displayName(),
                storageService.resolveUserAvatarUrl(row.avatarUrl()),
                row.totalXp(),
                row.currentLevel(),
                row.completedRuns(),
                row.countriesSolved(),
                row.perfectRuns(),
                row.solveRate(),
                row.currentStreak(),
                row.bestStreak(),
                row.leaderboardRank(),
                row.favoriteDifficulty(),
                xpForCurrent,
                xpForNext,
                divisor,
                streakBonusPercent,
                freezesAvailable,
                isOwnProfile ? row.profileLocked() : null,
                isOwnProfile ? row.socialDiscoverable() : null,
                isOwnProfile ? row.todayCompleted() : null,
                resetAtUtc,
                isOwnProfile ? row.freezeUsedToday() : null,
                previousFreezeAtStreak,
                freezeMax,
                nextFreezeAtStreak,
                isOwnProfile ? row.streakFreezesUsedSinceCompletion() : null,
                freezeNoticeAcknowledged,
                difficultyRecommendation,
                new BudgetProfileStatsDto(
                        row.budgetCompletedChallenges(),
                        row.budgetProjectsExplored(),
                        row.budgetProjectsMasteredValueUsd()));
    }

    private String resolveDifficultyRecommendation(
            List<ProfileDifficultyStatsDto> difficultyStats) {
        String lastCompletedLevel = findLastCompletedLevel(difficultyStats);
        return difficultyStats.stream()
                .filter(stats -> stats.completedRuns() >= MIN_RECOMMENDATION_RUNS)
                .filter(stats -> stats.solveRate() >= INCREASE_RECOMMENDATION_SOLVE_RATE)
                .filter(stats -> shouldRecommendIncrease(stats, lastCompletedLevel))
                .max(Comparator.comparingInt(stats -> difficultyIndex(stats.level())))
                .map(stats -> nextDifficulty(stats.level()))
                .orElse(null);
    }

    private String findLastCompletedLevel(List<ProfileDifficultyStatsDto> difficultyStats) {
        return difficultyStats.stream()
                .filter(stats -> stats.lastCompletedAt() != null)
                .max(Comparator.comparing(ProfileDifficultyStatsDto::lastCompletedAt))
                .map(ProfileDifficultyStatsDto::level)
                .orElse(null);
    }

    private boolean shouldRecommendIncrease(ProfileDifficultyStatsDto stats, String lastCompletedLevel) {
        String recommendedLevel = nextDifficulty(stats.level());
        return recommendedLevel != null
                && (lastCompletedLevel == null
                        || difficultyIndex(recommendedLevel) > difficultyIndex(lastCompletedLevel));
    }

    private String nextDifficulty(String level) {
        int index = difficultyIndex(level);
        if (index < 0 || index >= DIFFICULTY_ORDER.size() - 1) {
            return null;
        }
        return DIFFICULTY_ORDER.get(index + 1);
    }

    private int difficultyIndex(String level) {
        return DIFFICULTY_ORDER.indexOf(level);
    }

    private String computeResetAtUtc() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toString();
    }

    private String normalizeOwnedAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        if (!storageService.isMinioUrl(avatarUrl)) {
            throw new IllegalArgumentException("External avatar URLs are not allowed");
        }
        return avatarUrl;
    }

    private List<ProfileBadgeDto> resolveBadgeImageUrls(List<ProfileBadgeDto> badges) {
        return badges.stream().map(this::resolveBadgeImageUrl).toList();
    }

    private ProfileBadgeDto resolveBadgeImageUrl(ProfileBadgeDto badge) {
        return new ProfileBadgeDto(
                badge.id(),
                badge.slug(),
                badgeAssetService.resolveUrl(badge.id(), badge.imageUrl()),
                badge.accentColorKey(),
                badge.effectColorKey(),
                badge.name(),
                badge.description(),
                badge.unlocked(),
                badge.grantedAt(),
                badge.progress());
    }

    private UnacknowledgedBadgeDto resolveBadgeImageUrl(UnacknowledgedBadgeDto badge) {
        return new UnacknowledgedBadgeDto(
                badge.id(),
                badge.badgeId(),
                badge.slug(),
                badgeAssetService.resolveUrl(badge.badgeId(), badge.imageUrl()),
                badge.accentColorKey(),
                badge.effectColorKey(),
                badge.name(),
                badge.description(),
                badge.grantedAt());
    }
}
