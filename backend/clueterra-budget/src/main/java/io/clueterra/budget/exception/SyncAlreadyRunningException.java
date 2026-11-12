package io.clueterra.budget.exception;

public class SyncAlreadyRunningException extends RuntimeException {

    public SyncAlreadyRunningException(String message) {
        super(message);
    }
}
