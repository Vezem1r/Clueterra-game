package io.clueterra.api.exception;

public class ProfileLockedException extends RuntimeException {

    private static final String MESSAGE = "Profile is locked and cannot be modified";

    public ProfileLockedException() {
        super(MESSAGE);
    }
}
