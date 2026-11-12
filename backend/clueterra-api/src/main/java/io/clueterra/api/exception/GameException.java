package io.clueterra.api.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class GameException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public GameException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static GameException sessionNotFound(Long sessionId) {
        return new GameException(
                "Game session not found: " + sessionId, HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND");
    }

    public static GameException sessionCompleted() {
        return new GameException(
                "Game session is already completed", HttpStatus.BAD_REQUEST, "SESSION_COMPLETED");
    }

    public static GameException noMoreAttempts() {
        return new GameException(
                "No more attempts remaining", HttpStatus.BAD_REQUEST, "NO_ATTEMPTS_LEFT");
    }

    public static GameException noMoreHints() {
        return new GameException("No more hints to reveal", HttpStatus.BAD_REQUEST, "NO_HINTS_LEFT");
    }

    public static GameException alreadyPlayedToday() {
        return new GameException(
                "You have already played today's challenge", HttpStatus.CONFLICT, "ALREADY_PLAYED_TODAY");
    }

    public static GameException challengeNotFound() {
        return new GameException(
                "Today's challenge is being generated. Please try again in a few seconds.",
                HttpStatus.NOT_FOUND, "CHALLENGE_GENERATING");
    }

    public static GameException todaySessionNotFound() {
        return new GameException(
                "Today's game session was not found", HttpStatus.NOT_FOUND, "TODAY_SESSION_NOT_FOUND");
    }

    public static GameException resultsUnavailable() {
        return new GameException(
                "Results are available only after today's challenge is completed",
                HttpStatus.CONFLICT,
                "RESULTS_UNAVAILABLE");
    }

    public static GameException invalidCountry(String countryCode) {
        return new GameException(
                "Invalid country code: " + countryCode, HttpStatus.BAD_REQUEST, "INVALID_COUNTRY");
    }

    public static GameException unauthorized() {
        return new GameException(
                "You must be logged in to perform this action", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static GameException sessionAccessDenied() {
        return new GameException(
                "You don't have access to this session", HttpStatus.FORBIDDEN, "SESSION_ACCESS_DENIED");
    }

    public static GameException sessionNotInProgress() {
        return new GameException(
                "Session is not in progress", HttpStatus.BAD_REQUEST, "SESSION_NOT_IN_PROGRESS");
    }

    public static GameException hintRevealNotAllowed() {
        return new GameException(
                "Cannot reveal: need at least 1 attempt to guess",
                HttpStatus.BAD_REQUEST,
                "HINT_REVEAL_NOT_ALLOWED");
    }
}
