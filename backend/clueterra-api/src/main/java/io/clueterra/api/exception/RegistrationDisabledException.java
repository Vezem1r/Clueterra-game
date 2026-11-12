package io.clueterra.api.exception;

public class RegistrationDisabledException extends RuntimeException {

    private static final String MESSAGE = "Registration is currently disabled";

    public RegistrationDisabledException() {
        super(MESSAGE);
    }
}
