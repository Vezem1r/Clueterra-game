package io.clueterra.api.flaggame.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import io.clueterra.api.assets.service.FlagAssetPayload;
import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.flaggame.dto.FlagAnswerDto;
import io.clueterra.api.flaggame.dto.FlagGuessRequest;
import io.clueterra.api.flaggame.dto.FlagGuessResultDto;
import io.clueterra.api.flaggame.dto.FlagQuestionDto;
import io.clueterra.api.flaggame.dto.FlagRevealDto;
import io.clueterra.api.flaggame.dto.FlagSessionDto;
import io.clueterra.api.flaggame.model.FlagGuestStartResult;
import io.clueterra.api.flaggame.service.FlagGameService;
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;
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
@RequestMapping("/api/flag-game")
@RequiredArgsConstructor
@Validated
@Tag(name = "Flags", description = "Resumable guess-the-flag game that raises flags on a personal plaza")
public class FlagGameController {

    private final FlagGameService flagGameService;
    private final AuthService authService;

    @PostMapping("/session/guest")
    @Operation(summary = "Create or resume a flag game as a guest")
    public ResponseEntity<FlagSessionDto> startGuest(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        if (principal != null && !principal.isGuest()) {
            throw new ResponseStatusException(CONFLICT, "Registered players must use the player session");
        }
        FlagGuestStartResult started = flagGameService.startGuest(
                guestToken, request.termsVersion(), request.privacyVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(started.guestToken()).toString());
        return noStore(started.session());
    }

    @PostMapping("/session")
    @RegisteredUserOnly
    @Operation(summary = "Create or resume a flag game for a registered player")
    public ResponseEntity<FlagSessionDto> startRegistered(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.startRegistered(principal.getId()));
    }

    @PostMapping("/session/next")
    @Operation(summary = "Move a started player to the next flag")
    public ResponseEntity<FlagSessionDto> next(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.next(playerId(principal)));
    }

    @GetMapping("/session")
    @Operation(summary = "Get the player's plaza, totals and current flag without exposing its answer")
    public ResponseEntity<FlagSessionDto> session(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.session(principal == null ? null : principal.getId()));
    }

    @GetMapping("/rounds/{round}/flag.svg")
    @Operation(summary = "Get the current flag image without its country code in the URL or the markup")
    public ResponseEntity<byte[]> roundFlag(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        FlagAssetPayload flag = flagGameService.roundFlag(playerId(principal), round);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(flag.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(flag.bytes());
    }

    @PostMapping("/rounds/{round}/hint")
    @Operation(summary = "Reveal the next clue: region, first letter, then capital")
    public ResponseEntity<FlagQuestionDto> revealHint(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.revealHint(playerId(principal), round));
    }

    @PostMapping("/rounds/{round}/guess")
    @Operation(summary = "Submit a country; wrong guesses do not end the round")
    public ResponseEntity<FlagGuessResultDto> guess(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FlagGuessRequest request) {
        return noStore(flagGameService.guess(playerId(principal), round, request.countryCode()));
    }

    @PostMapping("/rounds/{round}/answer")
    @Operation(summary = "Give up on the current flag and reveal the country without XP")
    public ResponseEntity<FlagAnswerDto> showAnswer(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.showAnswer(playerId(principal), round));
    }

    @PostMapping("/rounds/{round}/skip")
    @Operation(summary = "Defer a flag without counting an answer attempt")
    public ResponseEntity<FlagSessionDto> skip(
            @PathVariable @Positive long round,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.skip(playerId(principal), round));
    }

    @GetMapping("/countries/{countryCode}")
    @Operation(summary = "Get the card of a country whose flag this player has already raised or revealed")
    public ResponseEntity<FlagRevealDto> discoveredCountry(
            @PathVariable @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(flagGameService.discoveredCountry(principal == null ? null : principal.getId(), countryCode));
    }

    private long playerId(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Start a flag game first");
        }
        return principal.getId();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
