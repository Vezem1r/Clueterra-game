package io.clueterra.api.rivergame.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.auth.service.AuthService;
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.rivergame.dto.RiverAnswerDto;
import io.clueterra.api.rivergame.dto.RiverGuessRequest;
import io.clueterra.api.rivergame.dto.RiverGuessResultDto;
import io.clueterra.api.rivergame.dto.RiverQuestionDto;
import io.clueterra.api.rivergame.dto.RiverSessionDto;
import io.clueterra.api.rivergame.model.RiverGuestStartResult;
import io.clueterra.api.rivergame.service.RiverGameService;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/api/rivers")
@RequiredArgsConstructor
@Tag(name = "Rivers", description = "Resumable river identification game")
public class RiverGameController {

    private final RiverGameService riverGameService;
    private final AuthService authService;

    @PostMapping("/session/guest")
    @Operation(summary = "Create or resume a river game as a guest")
    public ResponseEntity<RiverQuestionDto> startGuest(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        if (principal != null && !principal.isGuest()) {
            throw new ResponseStatusException(CONFLICT, "Registered players must use the player session");
        }
        RiverGuestStartResult started = riverGameService.startGuest(
                guestToken, request.termsVersion(), request.privacyVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(started.guestToken()).toString());
        return noStore(started.question());
    }

    @PostMapping("/session")
    @RegisteredUserOnly
    @Operation(summary = "Create or resume a river game for a registered player")
    public ResponseEntity<RiverQuestionDto> startRegistered(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.startRegistered(principal.getId()));
    }

    @PostMapping("/session/next")
    @Operation(summary = "Move a started river player to the next river")
    public ResponseEntity<RiverQuestionDto> next(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.next(playerId(principal)));
    }

    @GetMapping("/session")
    @Operation(summary = "Get this river player's totals and current river without exposing its answer")
    public ResponseEntity<RiverSessionDto> session(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.session(principal == null ? null : principal.getId()));
    }

    @PostMapping("/questions/{questionId}/hint")
    @Operation(summary = "Reveal the next clue and switch to the border map at clue two")
    public ResponseEntity<RiverQuestionDto> revealHint(
            @PathVariable @Positive long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.revealHint(playerId(principal), questionId));
    }

    @PostMapping("/questions/{questionId}/guess")
    @Operation(summary = "Submit a river name; wrong guesses do not end the question")
    public ResponseEntity<RiverGuessResultDto> guess(
            @PathVariable @Positive long questionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RiverGuessRequest request) {
        return noStore(riverGameService.guess(playerId(principal), questionId, request.answer()));
    }

    @PostMapping("/questions/{questionId}/answer")
    @Operation(summary = "Give up on the current river and reveal its answer without XP")
    public ResponseEntity<RiverAnswerDto> showAnswer(
            @PathVariable @Positive long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.showAnswer(playerId(principal), questionId));
    }

    @PostMapping("/questions/{questionId}/skip")
    @Operation(summary = "Defer a river without counting an answer attempt")
    public ResponseEntity<RiverQuestionDto> skip(
            @PathVariable @Positive long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(riverGameService.skip(playerId(principal), questionId));
    }

    @GetMapping("/names")
    @Operation(summary = "Search known river names for answer autocomplete")
    public ResponseEntity<List<String>> searchNames(@RequestParam String q) {
        return noStore(riverGameService.searchNames(q));
    }

    private long playerId(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Start a river game first");
        }
        return principal.getId();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
