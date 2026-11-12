package io.clueterra.budget.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
        Instant timestamp, int status, String error, String message, Map<String, String> errors) {

    public static ApiErrorResponse of(HttpStatus status, String message) {
        return new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, null);
    }

    public static ApiErrorResponse validation(Map<String, String> errors) {
        return new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Validation failed",
                errors);
    }
}
