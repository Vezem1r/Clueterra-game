package io.clueterra.api.quizgame.controller;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.quizgame.dto.QuizAnswerDto;
import io.clueterra.api.quizgame.dto.QuizAnswerRequest;
import io.clueterra.api.quizgame.dto.QuizSessionDto;
import io.clueterra.api.quizgame.model.QuizGuestStartResult;
import io.clueterra.api.quizgame.service.QuizGameService;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/quiz-game")
@RequiredArgsConstructor
@Validated
@Tag(name = "World quiz", description = "Resumable world tour quiz that drives or flies from country to country")
public class QuizGameController {

    private final QuizGameService quizGameService;
    private final AuthService authService;

    @PostMapping("/session/guest")
    @Operation(summary = "Create or resume a world tour as a guest")
    public ResponseEntity<QuizSessionDto> startGuest(
            @CookieValue(name = "ct_guest", required = false) String guestToken,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LegalAcceptanceRequest request,
            HttpServletResponse response) {
        if (principal != null && !principal.isGuest()) {
            throw new ResponseStatusException(CONFLICT, "Registered players must use the player session");
        }
        QuizGuestStartResult started = quizGameService.startGuest(
                guestToken, request.termsVersion(), request.privacyVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildGuestCookie(started.guestToken()).toString());
        return noStore(started.session());
    }

    @PostMapping("/session")
    @RegisteredUserOnly
    @Operation(summary = "Create or resume a world tour for a registered player")
    public ResponseEntity<QuizSessionDto> startRegistered(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(quizGameService.startRegistered(principal.getId()));
    }

    @GetMapping("/session")
    @Operation(summary = "Get the player's route, totals and current stop without unanswered solutions")
    public ResponseEntity<QuizSessionDto> session(@AuthenticationPrincipal UserPrincipal principal) {
        return noStore(quizGameService.session(principal == null ? null : principal.getId()));
    }

    @PostMapping("/stops/{stop}/questions/{position}/answer")
    @Operation(summary = "Answer one question of the current stop; the second answer finishes the stop and picks the next destination")
    public ResponseEntity<QuizAnswerDto> answer(
            @PathVariable @Positive long stop,
            @PathVariable @Min(1) @Max(2) int position,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody QuizAnswerRequest request) {
        return noStore(quizGameService.answer(playerId(principal), stop, position, request.option()));
    }

    @PostMapping("/stops/{stop}/travel")
    @Operation(summary = "Drive or fly from a finished stop to the next destination")
    public ResponseEntity<QuizSessionDto> travel(
            @PathVariable @Positive long stop,
            @AuthenticationPrincipal UserPrincipal principal) {
        return noStore(quizGameService.travel(playerId(principal), stop));
    }

    @GetMapping("/stops/{stop}/questions/{position}/options/{option}/flag.svg")
    @Operation(summary = "Get a flag answer option without its country code in the URL or the markup")
    public ResponseEntity<byte[]> optionFlag(
            @PathVariable @Positive long stop,
            @PathVariable @Min(1) @Max(2) int position,
            @PathVariable @Min(0) @Max(3) int option,
            @AuthenticationPrincipal UserPrincipal principal) {
        FlagAssetPayload flag = quizGameService.optionFlag(playerId(principal), stop, position, option);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(flag.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(flag.bytes());
    }

    private long playerId(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Start a world tour first");
        }
        return principal.getId();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
