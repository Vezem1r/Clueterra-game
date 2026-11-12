package io.clueterra.api.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String code,
        Map<String, String> errors,
        Long retryAfterSeconds) {

    public static ApiErrorResponse of(HttpStatus status, String message) {
        return new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, null, null, null);
    }

    public static ApiErrorResponse ofCode(HttpStatus status, String message, String code) {
        return new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, code, null, null);
    }

    public static ApiErrorResponse ofGame(HttpStatus status, String message, String code) {
        return new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, code, null, null);
    }

    public static ApiErrorResponse ofValidation(Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Validation failed",
                null,
                fieldErrors,
                null);
    }

    public static ApiErrorResponse ofRateLimit(long retryAfterSeconds) {
        return new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please try again in " + retryAfterSeconds + " seconds.",
                null,
                null,
                retryAfterSeconds);
    }
}
