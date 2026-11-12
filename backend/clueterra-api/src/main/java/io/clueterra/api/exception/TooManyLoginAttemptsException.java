package io.clueterra.api.exception;

import lombok.Getter;

@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    private static final String MESSAGE = "Too many login attempts for this account";

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super(MESSAGE);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
