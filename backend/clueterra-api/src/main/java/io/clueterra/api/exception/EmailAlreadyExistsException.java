package io.clueterra.api.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Unable to create account with the provided details");
    }
}
