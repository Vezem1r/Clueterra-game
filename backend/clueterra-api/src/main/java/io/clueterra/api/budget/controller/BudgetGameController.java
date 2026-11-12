package io.clueterra.api.budget.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.budget.dto.BudgetAnswerRequest;
import io.clueterra.api.budget.dto.BudgetAnswerResultDto;
import io.clueterra.api.budget.dto.BudgetGameSessionDto;
import io.clueterra.api.budget.dto.BudgetHintRequest;
import io.clueterra.api.budget.dto.BudgetHintResultDto;
import io.clueterra.api.budget.dto.BudgetTodayChallengeDto;
import io.clueterra.api.budget.service.BudgetGameSessionService;
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
@Validated
@Tag(name = "BudgetGuessr", description = "Daily megaproject budget challenge")
public class BudgetGameController {

    private final BudgetGameSessionService gameService;
    private final AuthService authService;

    @GetMapping("/challenges/today")
    @Operation(summary = "Get availability of today's five-question BudgetGuessr challenge")
    public ResponseEntity<BudgetTodayChallengeDto> getTodayChallenge() {
        return noStore(gameService.getTodayChallenge());
    }

    @PostMapping("/sessions/guest")
    @Operation(summary = "Create or resume today's BudgetGuessr session as a guest")
    public ResponseEntity<BudgetGameSessionDto> startGuestSession(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        BudgetGameSessionDto session = gameService.startGuestSession(
                request.termsVersion(), request.privacyVersion(), guestToken);

        if (principal != null && !principal.isGuest()) {
            response.addHeader(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString());
            response.addHeader(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString());
        }

        String newToken = gameService.getGuestToken(session.id());
        if (newToken != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(newToken).toString());
        }
        return noStore(session);
    }

    @PostMapping("/sessions")
    @RegisteredUserOnly
    @Operation(summary = "Create or resume today's BudgetGuessr session")
    public ResponseEntity<BudgetGameSessionDto> startUserSession(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(gameService.startUserSession(principal.getId()));
    }

    @GetMapping("/sessions/today")
    @Operation(summary = "Resume the current player's BudgetGuessr session for today")
    public ResponseEntity<BudgetGameSessionDto> getTodaySession(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return noContent();
        }
        return gameService.getTodaySession(principal.getId())
                .map(this::noStore)
                .orElseGet(this::noContent);
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get an owned BudgetGuessr session without unrevealed answers")
    public ResponseEntity<BudgetGameSessionDto> getSession(@PathVariable @Positive long sessionId, @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal == null ? null : principal.getId();
        return noStore(gameService.getSession(sessionId, userId));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    @Operation(summary = "Answer the current BudgetGuessr question and reveal its result")
    public ResponseEntity<BudgetAnswerResultDto> submitAnswer(
            @PathVariable @Positive long sessionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BudgetAnswerRequest request) {
        Long userId = principal == null ? null : principal.getId();
        return noStore(gameService.submitAnswer(sessionId, userId, request));
    }

    @PostMapping("/sessions/{sessionId}/hints")
    @Operation(summary = "Reveal the next hint for the current BudgetGuessr question")
    public ResponseEntity<BudgetHintResultDto> revealHint(
            @PathVariable @Positive long sessionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BudgetHintRequest request) {
        Long userId = principal == null ? null : principal.getId();
        return noStore(gameService.revealHint(sessionId, userId, request));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private <T> ResponseEntity<T> noContent() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
