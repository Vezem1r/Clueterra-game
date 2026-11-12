package io.clueterra.api.leaderboard.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import io.clueterra.api.config.CacheConfig;
import io.clueterra.api.leaderboard.dto.LeaderboardEntryDto;
import io.clueterra.api.leaderboard.dto.LeaderboardPageResponse;
import io.clueterra.api.leaderboard.repository.LeaderboardJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardJdbcRepository leaderboardRepository;
    private final StorageService storageService;

    private static final int MAX_PAGE_SIZE = 100;

    @Cacheable(value = CacheConfig.LEADERBOARD_CACHE, cacheManager = "redisCacheManager",
            key = "'page_' + #page + '_size_' + #size")
    public LeaderboardPageResponse getLeaderboard(int page, int size) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        int offset = page * clampedSize;
        LeaderboardPageResponse response = leaderboardRepository.findPage(page, clampedSize, offset);
        return new LeaderboardPageResponse(
                response.entries().stream()
                        .map(this::resolveAvatarUrl)
                        .toList(),
                response.page(),
                response.size(),
                response.totalElements(),
                response.totalPages());
    }

    private LeaderboardEntryDto resolveAvatarUrl(LeaderboardEntryDto entry) {
        return new LeaderboardEntryDto(
                entry.userId(),
                entry.username(),
                entry.displayName(),
                storageService.resolveUserAvatarUrl(entry.avatarUrl()),
                entry.totalXp(),
                entry.level(),
                entry.challengesWon(),
                entry.rank());
    }
}
