package io.clueterra.budget.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SyncAlreadyRunningException.class)
    public ResponseEntity<ApiErrorResponse> handleSyncAlreadyRunning(
            SyncAlreadyRunningException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ExternalSourceException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalSource(ExternalSourceException exception) {
        log.warn("External budget source failed: {}", exception.getMessage());
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected BudgetGuessr content service error: {}", exception.getMessage(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message));
    }
}
