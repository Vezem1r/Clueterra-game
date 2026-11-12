package io.clueterra.api.friends.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.config.CacheConfig;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.friends.dto.FriendDto;
import io.clueterra.api.friends.dto.FriendLeaderboardEntryDto;
import io.clueterra.api.friends.dto.FriendRequestDto;
import io.clueterra.api.friends.dto.FriendRequestsDto;
import io.clueterra.api.friends.dto.FriendUserDto;
import io.clueterra.api.friends.repository.FriendLeaderboardEntryRow;
import io.clueterra.api.friends.repository.FriendRequestRow;
import io.clueterra.api.friends.repository.FriendRow;
import io.clueterra.api.friends.repository.FriendUserRow;
import io.clueterra.api.friends.repository.FriendsJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendsService {

    private static final int DEFAULT_SEARCH_LIMIT = 12;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_FRIENDS = 50;
    private static final int MAX_OUTGOING_PENDING_REQUESTS = 25;
    private static final String PERIOD_WEEK = "week";
    private static final String PERIOD_ALL_TIME = "alltime";

    private final FriendsJdbcRepository friendsRepository;
    private final StorageService storageService;
    private final Clock clock;

    public List<FriendUserDto> searchUsers(Long userId, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        return friendsRepository.searchUsers(userId, normalizedQuery, DEFAULT_SEARCH_LIMIT).stream()
                .map(this::mapUser)
                .toList();
    }

    public List<FriendDto> getFriends(Long userId) {
        return friendsRepository.findFriends(userId).stream().map(this::mapFriend).toList();
    }

    public FriendRequestsDto getFriendRequests(Long userId) {
        return new FriendRequestsDto(
                friendsRepository.findIncomingRequests(userId).stream().map(this::mapFriendRequest).toList(),
                friendsRepository.findOutgoingRequests(userId).stream().map(this::mapFriendRequest).toList());
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void requestFriend(Long userId, Long targetUserId) {
        validateDifferentUsers(userId, targetUserId);
        validateEligibleUser(targetUserId);
        validateFriendCapacity(userId);
        validateFriendCapacity(targetUserId);
        if (friendsRepository.countOutgoingPending(userId) >= MAX_OUTGOING_PENDING_REQUESTS) {
            throw new ResourceConflictException("Too many pending friend requests");
        }
        friendsRepository.insertFriendRequest(userId, targetUserId)
                .orElseThrow(() -> new ResourceConflictException("Friendship already exists or cannot be requested"));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void acceptFriendRequest(Long userId, Long requestId) {
        validateFriendCapacity(userId);
        if (!friendsRepository.acceptFriendRequest(userId, requestId)) {
            throw new ResourceNotFoundException("Friend request not found");
        }
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void deleteFriendRequest(Long userId, Long requestId) {
        if (friendsRepository.deleteFriendRequest(userId, requestId) == 0) {
            throw new ResourceNotFoundException("Friend request not found");
        }
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void removeFriend(Long userId, Long friendId) {
        if (friendsRepository.deleteFriendship(userId, friendId) == 0) {
            throw new ResourceNotFoundException("Friendship not found");
        }
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void blockUser(Long userId, Long blockedUserId) {
        validateDifferentUsers(userId, blockedUserId);
        validateEligibleUser(blockedUserId);
        friendsRepository.blockUser(userId, blockedUserId);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FRIENDS_LEADERBOARD_CACHE, cacheManager = "redisCacheManager", allEntries = true)
    public void unblockUser(Long userId, Long blockedUserId) {
        validateDifferentUsers(userId, blockedUserId);
        if (friendsRepository.unblockUser(userId, blockedUserId) == 0) {
            throw new ResourceNotFoundException("Blocked user not found");
        }
    }

    @Cacheable(
            value = CacheConfig.FRIENDS_LEADERBOARD_CACHE,
            cacheManager = "redisCacheManager",
            key = "'friends_' + #userId + '_period_' + #period")
    public List<FriendLeaderboardEntryDto> getFriendsLeaderboard(Long userId, String period) {
        return mapLeaderboard(friendsRepository.findFriendsLeaderboard(userId, normalizePeriod(period), currentWeekStart()));
    }

    private FriendDto mapFriend(FriendRow row) {
        return new FriendDto(stringify(row.friendshipId()), mapUser(row.user()), row.friendsSince());
    }

    private FriendRequestDto mapFriendRequest(FriendRequestRow row) {
        return new FriendRequestDto(stringify(row.requestId()), mapUser(row.user()), row.direction(), row.createdAt());
    }

    private List<FriendLeaderboardEntryDto> mapLeaderboard(List<FriendLeaderboardEntryRow> rows) {
        return rows.stream()
                .map(row -> new FriendLeaderboardEntryDto(
                        row.rank(),
                        mapUser(row.user()),
                        row.scoreXp(),
                        row.totalXp(),
                        row.level(),
                        row.currentUser()))
                .toList();
    }

    private FriendUserDto mapUser(FriendUserRow row) {
        return new FriendUserDto(
                stringify(row.userId()),
                row.username(),
                row.displayName(),
                storageService.resolveUserAvatarUrl(row.avatarUrl()),
                row.totalXp(),
                row.level());
    }

    private void validateDifferentUsers(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Target user must be different");
        }
    }

    private void validateEligibleUser(Long userId) {
        if (!friendsRepository.isEligibleRegisteredUser(userId)) {
            throw new ResourceNotFoundException("Target user not found");
        }
    }

    private void validateFriendCapacity(Long userId) {
        if (friendsRepository.countAcceptedFriends(userId) >= MAX_FRIENDS) {
            throw new ResourceConflictException("Friend limit reached");
        }
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank() || PERIOD_WEEK.equalsIgnoreCase(period)) {
            return PERIOD_WEEK;
        }
        if (PERIOD_ALL_TIME.equalsIgnoreCase(period)) {
            return PERIOD_ALL_TIME;
        }
        throw new IllegalArgumentException("Unsupported leaderboard period");
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String stringify(Long id) {
        return id == null ? null : id.toString();
    }
}
