package io.clueterra.api.friends.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.friends.repository.FriendsJdbcRepository;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class FriendsServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long TARGET_USER_ID = 20L;

    @Mock
    private FriendsJdbcRepository friendsRepository;

    @Mock
    private StorageService storageService;

    private FriendsService friendsService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC);
        friendsService = new FriendsService(friendsRepository, storageService, clock);
    }

    @Test
    @DisplayName("short user search returns empty result without hitting database")
    void searchUsers_shortQuery_returnsEmpty() {
        assertThat(friendsService.searchUsers(USER_ID, "a")).isEmpty();

        verify(friendsRepository, never()).searchUsers(org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("friend request rejects missing or guest target users")
    void requestFriend_targetMissing_throwsNotFound() {
        when(friendsRepository.isEligibleRegisteredUser(TARGET_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> friendsService.requestFriend(USER_ID, TARGET_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Target user not found");
    }

    @Test
    @DisplayName("friend request enforces outgoing pending request limit")
    void requestFriend_tooManyPending_throwsConflict() {
        when(friendsRepository.isEligibleRegisteredUser(TARGET_USER_ID)).thenReturn(true);
        when(friendsRepository.countAcceptedFriends(USER_ID)).thenReturn(1);
        when(friendsRepository.countAcceptedFriends(TARGET_USER_ID)).thenReturn(1);
        when(friendsRepository.countOutgoingPending(USER_ID)).thenReturn(25);

        assertThatThrownBy(() -> friendsService.requestFriend(USER_ID, TARGET_USER_ID))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Too many pending friend requests");
    }

    @Test
    @DisplayName("friends leaderboard rejects unsupported periods before querying database")
    void getFriendsLeaderboard_unsupportedPeriod_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> friendsService.getFriendsLeaderboard(USER_ID, "month"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported leaderboard period");

        verify(friendsRepository, never()).findFriendsLeaderboard(
                org.mockito.ArgumentMatchers.any(),
                anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("friends list defaults to empty when repository has no rows")
    void getFriends_emptyRows_returnsEmpty() {
        when(friendsRepository.findFriends(USER_ID)).thenReturn(List.of());

        assertThat(friendsService.getFriends(USER_ID)).isEmpty();
    }
}
