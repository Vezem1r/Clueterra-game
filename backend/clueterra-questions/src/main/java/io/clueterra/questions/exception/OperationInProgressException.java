package io.clueterra.questions.exception;

public class OperationInProgressException extends ConflictException {

    public OperationInProgressException(String runningOperation) {
        super("Another Questions operation is already running: " + runningOperation);
    }
}
