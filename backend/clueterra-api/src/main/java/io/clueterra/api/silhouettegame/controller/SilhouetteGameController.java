package io.clueterra.api.silhouettegame.controller;

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
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;
import io.clueterra.api.silhouettegame.dto.SilhouetteAnswerDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteGuessRequest;
import io.clueterra.api.silhouettegame.dto.SilhouetteGuessResultDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteQuestionDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteRevealDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteSessionDto;
import io.clueterra.api.silhouettegame.model.SilhouetteGuestStartResult;
import io.clueterra.api.silhouettegame.service.SilhouetteGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/silhouettes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Silhouettes", description = "Resumable country silhouette game that paints a personal globe")
public class SilhouetteGameController {

    private final SilhouetteGameService silhouetteGameService;
    private final AuthService authService;

    @PostMapping("/session/guest")
    @Operation(summary = "Create or resume a silhouette game as a guest")
    public ResponseEntity<SilhouetteSessionDto> startGuest(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        if (principal != null && !principal.isGuest()) {
            throw new ResponseStatusException(CONFLICT, "Registered players must use the player session");
        }
        SilhouetteGuestStartResult started = silhouetteGameService.startGuest(
                guestToken, request.termsVersion(), request.privacyVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(started.guestToken()).toString());
        return noStore(started.session());
    }

    @PostMapping("/session")
    @RegisteredUserOnly
    @Operation(summary = "Create or resume a silhouette game for a registered player")
    public ResponseEntity<SilhouetteSessionDto> startRegistered(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.startRegistered(principal.getId()));
    }

    @PostMapping("/session/next")
    @Operation(summary = "Move a started player to the next silhouette")
    public ResponseEntity<SilhouetteSessionDto> next(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.next(playerId(principal)));
    }

    @GetMapping("/session")
    @Operation(summary = "Get the player's globe, totals and current silhouette without exposing its answer")
    public ResponseEntity<SilhouetteSessionDto> session(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.session(principal == null ? null : principal.getId()));
    }

    @PostMapping("/rounds/{round}/hint")
    @Operation(summary = "Reveal the next clue: region, surroundings, flag colours, then location")
    public ResponseEntity<SilhouetteQuestionDto> revealHint(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.revealHint(playerId(principal), round));
    }

    @PostMapping("/rounds/{round}/guess")
    @Operation(summary = "Submit a country; wrong guesses do not end the round")
    public ResponseEntity<SilhouetteGuessResultDto> guess(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SilhouetteGuessRequest request) {
        return noStore(silhouetteGameService.guess(playerId(principal), round, request.countryCode()));
    }

    @PostMapping("/rounds/{round}/answer")
    @Operation(summary = "Give up on the current silhouette and reveal the country without XP")
    public ResponseEntity<SilhouetteAnswerDto> showAnswer(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.showAnswer(playerId(principal), round));
    }

    @PostMapping("/rounds/{round}/skip")
    @Operation(summary = "Defer a silhouette without counting an answer attempt")
    public ResponseEntity<SilhouetteSessionDto> skip(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.skip(playerId(principal), round));
    }

    @GetMapping("/countries/{countryCode}")
    @Operation(summary = "Get the photo and facts card of a country this player has already guessed")
    public ResponseEntity<SilhouetteRevealDto> discoveredCountry(
            @PathVariable @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(silhouetteGameService.discoveredCountry(principal == null ? null : principal.getId(), countryCode));
    }

    private long playerId(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Start a silhouette game first");
        }
        return principal.getId();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
