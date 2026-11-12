package io.clueterra.api.budget.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class BudgetGameException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    private BudgetGameException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static BudgetGameException fromOutcome(String outcome, Long sessionId) {
        return switch (outcome) {
            case "CHALLENGE_NOT_FOUND" -> new BudgetGameException(
                    "Today's BudgetGuessr challenge is not available",
                    HttpStatus.NOT_FOUND,
                    "BUDGET_CHALLENGE_NOT_FOUND");
            case "INVALID_CHALLENGE" -> new BudgetGameException(
                    "Today's BudgetGuessr challenge is incomplete",
                    HttpStatus.CONFLICT,
                    "BUDGET_CHALLENGE_INVALID");
            case "USER_NOT_FOUND" -> new BudgetGameException(
                    "Game user is not available", HttpStatus.UNAUTHORIZED, "BUDGET_USER_NOT_FOUND");
            case "SESSION_NOT_FOUND" -> sessionNotFound(sessionId);
            case "ACCESS_DENIED" -> accessDenied();
            case "SESSION_EXPIRED" -> sessionExpired();
            case "SESSION_NOT_IN_PROGRESS" -> new BudgetGameException(
                    "BudgetGuessr session is not in progress",
                    HttpStatus.CONFLICT,
                    "BUDGET_SESSION_NOT_IN_PROGRESS");
            case "STALE_QUESTION" -> new BudgetGameException(
                    "The submitted question is no longer current",
                    HttpStatus.CONFLICT,
                    "BUDGET_STALE_QUESTION");
            case "STALE_HINT" -> new BudgetGameException(
                    "The requested hint is no longer next",
                    HttpStatus.CONFLICT,
                    "BUDGET_STALE_HINT");
            case "HINT_NOT_AVAILABLE" -> new BudgetGameException(
                    "No more hints are available for this question",
                    HttpStatus.CONFLICT,
                    "BUDGET_HINT_NOT_AVAILABLE");
            case "INVALID_HINT" -> new BudgetGameException(
                    "The generated hint is invalid",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BUDGET_HINT_INVALID");
            case "INVALID_ANSWER", "INVALID_REQUEST" -> new BudgetGameException(
                    "Answer does not match the current question type",
                    HttpStatus.BAD_REQUEST,
                    "BUDGET_INVALID_ANSWER");
            case "INVALID_SETTINGS" -> new BudgetGameException(
                    "BudgetGuessr XP settings are invalid",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BUDGET_SETTINGS_INVALID");
            default -> new BudgetGameException(
                    "BudgetGuessr operation failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BUDGET_OPERATION_FAILED");
        };
    }

    public static BudgetGameException authenticationRequired() {
        return new BudgetGameException(
                "Authentication or a guest session is required",
                HttpStatus.UNAUTHORIZED,
                "BUDGET_AUTHENTICATION_REQUIRED");
    }

    public static BudgetGameException sessionNotFound(Long sessionId) {
        return new BudgetGameException(
                sessionId == null
                        ? "BudgetGuessr session not found"
                        : "BudgetGuessr session not found: " + sessionId,
                HttpStatus.NOT_FOUND,
                "BUDGET_SESSION_NOT_FOUND");
    }

    public static BudgetGameException accessDenied() {
        return new BudgetGameException(
                "You do not have access to this BudgetGuessr session",
                HttpStatus.FORBIDDEN,
                "BUDGET_SESSION_ACCESS_DENIED");
    }

    public static BudgetGameException sessionExpired() {
        return new BudgetGameException(
                "Only today's BudgetGuessr session can be played",
                HttpStatus.CONFLICT,
                "BUDGET_SESSION_EXPIRED");
    }
}
