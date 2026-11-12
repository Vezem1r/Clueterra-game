package io.clueterra.api.game.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.game.dto.ChallengeDto;
import io.clueterra.api.game.dto.CountryDto;
import io.clueterra.api.game.dto.GameResultsDto;
import io.clueterra.api.game.dto.GameSessionDto;
import io.clueterra.api.game.dto.GameSessionSummaryDto;
import io.clueterra.api.game.dto.GuessRequest;
import io.clueterra.api.game.dto.GuessResultDto;
import io.clueterra.api.game.dto.GuestProgressDto;
import io.clueterra.api.game.dto.GuestStateDto;
import io.clueterra.api.game.dto.RevealResultDto;
import io.clueterra.api.game.dto.StartSessionRequest;
import io.clueterra.api.game.service.GameResultsService;
import io.clueterra.api.game.service.GameSessionService;
import io.clueterra.api.game.service.GameplayService;
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@Tag(name = "Game", description = "Game session management and gameplay")
public class GameController {

    private final GameSessionService gameSessionService;
    private final GameplayService gameplayService;
    private final GameResultsService gameResultsService;
    private final AuthService authService;

    @GetMapping("/today/easy")
    public ResponseEntity<ChallengeDto> getTodaysEasyChallenge() {
        return ResponseEntity.ok(gameSessionService.getTodaysEasyChallenge());
    }

    @GetMapping("/today")
    public ResponseEntity<List<ChallengeDto>> getTodaysChallenges(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(gameSessionService.getTodaysChallenges(userId));
    }

    @GetMapping("/sessions/today")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameSessionDto> getTodaySession(
            @AuthenticationPrincipal UserPrincipal principal) {
        return gameSessionService.getTodaySession(principal.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/today/summary")
    public ResponseEntity<GameSessionSummaryDto> getTodaySessionSummary(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return gameSessionService.getTodaySessionSummary(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/guest/progress")
    public ResponseEntity<GuestProgressDto> getGuestProgress(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(gameSessionService.getGuestProgress(principal));
    }

    @GetMapping("/guest/state")
    public ResponseEntity<GuestStateDto> getGuestState(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(gameSessionService.getGuestState(principal));
    }

    @GetMapping("/results/today")
    public ResponseEntity<GameResultsDto> getTodayResults(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(gameResultsService.getTodayResults(userId));
    }

    @PostMapping("/sessions/guest")
    public ResponseEntity<GameSessionDto> startGuestSession(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        GameSessionDto session = gameSessionService.startGuestSession(request.termsVersion(), request.privacyVersion(), guestToken);

        if (principal != null && !principal.isGuest()) {
            response.addHeader(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString());
            response.addHeader(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString());
        }

        String newToken = gameSessionService.getGuestTokenForSession(session.id());
        if (newToken != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(newToken).toString());
        }

        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions")
    @RegisteredUserOnly
    public ResponseEntity<GameSessionDto> startUserSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StartSessionRequest request) {
        return ResponseEntity.ok(gameSessionService.startUserSession(principal.getId(), request.level()));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<GameSessionDto> getSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(gameSessionService.getSession(sessionId, userId));
    }

    @PostMapping("/sessions/{sessionId}/guess")
    public ResponseEntity<GuessResultDto> guess(
            @PathVariable Long sessionId,
            @Valid @RequestBody GuessRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(
                gameplayService.guess(sessionId, request.countryCode(), userId));
    }

    @PostMapping("/sessions/{sessionId}/reveal")
    public ResponseEntity<RevealResultDto> revealHint(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(gameplayService.revealHint(sessionId, userId));
    }

    @GetMapping("/countries/search")
    public ResponseEntity<List<CountryDto>> searchCountries(@RequestParam String q) {
        return ResponseEntity.ok(gameplayService.searchCountries(q));
    }
}
