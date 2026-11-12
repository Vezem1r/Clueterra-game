package io.clueterra.api.friends.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.friends.dto.FriendDto;
import io.clueterra.api.friends.dto.FriendLeaderboardEntryDto;
import io.clueterra.api.friends.dto.FriendRequestsDto;
import io.clueterra.api.friends.dto.FriendUserDto;
import io.clueterra.api.friends.service.FriendsService;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friends", description = "Friend requests, friends list, blocks and friends leaderboards")
public class FriendsController {

    private final FriendsService friendsService;

    @GetMapping("/users/search")
    @RegisteredUserOnly
    @Operation(summary = "Search registered users")
    public ResponseEntity<List<FriendUserDto>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @NotBlank String q) {
        return ResponseEntity.ok(friendsService.searchUsers(principal.getId(), q));
    }

    @GetMapping
    @RegisteredUserOnly
    @Operation(summary = "List friends")
    public ResponseEntity<List<FriendDto>> getFriends(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendsService.getFriends(principal.getId()));
    }

    @GetMapping("/requests")
    @RegisteredUserOnly
    @Operation(summary = "List friend requests")
    public ResponseEntity<FriendRequestsDto> getFriendRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendsService.getFriendRequests(principal.getId()));
    }

    @PostMapping("/requests/{targetUserId}")
    @RegisteredUserOnly
    @Operation(summary = "Send friend request")
    public ResponseEntity<Void> requestFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long targetUserId) {
        friendsService.requestFriend(principal.getId(), targetUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/requests/{requestId}/accept")
    @RegisteredUserOnly
    @Operation(summary = "Accept friend request")
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId) {
        friendsService.acceptFriendRequest(principal.getId(), requestId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/requests/{requestId}")
    @RegisteredUserOnly
    @Operation(summary = "Decline or cancel friend request")
    public ResponseEntity<Void> deleteFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId) {
        friendsService.deleteFriendRequest(principal.getId(), requestId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendId}")
    @RegisteredUserOnly
    @Operation(summary = "Remove friend")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long friendId) {
        friendsService.removeFriend(principal.getId(), friendId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/blocks/{blockedUserId}")
    @RegisteredUserOnly
    @Operation(summary = "Block user")
    public ResponseEntity<Void> blockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long blockedUserId) {
        friendsService.blockUser(principal.getId(), blockedUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    @RegisteredUserOnly
    @Operation(summary = "Unblock user")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long blockedUserId) {
        friendsService.unblockUser(principal.getId(), blockedUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/leaderboard")
    @RegisteredUserOnly
    @Operation(summary = "Get friends leaderboard")
    public ResponseEntity<List<FriendLeaderboardEntryDto>> getFriendsLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(friendsService.getFriendsLeaderboard(principal.getId(), period));
    }
}
