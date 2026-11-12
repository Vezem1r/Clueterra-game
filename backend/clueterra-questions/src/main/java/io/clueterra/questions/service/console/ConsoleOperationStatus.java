package io.clueterra.questions.service.console;

public record ConsoleOperationStatus(String id, String name, String status,
                                     long startedAt, Long completedAt, String error) {

    static final String RUNNING = "RUNNING";
    static final String COMPLETED = "COMPLETED";
    static final String FAILED = "FAILED";

    boolean isRunning() {
        return RUNNING.equals(status);
    }

    ConsoleOperationStatus finish(String finalStatus, long finishedAt, String failure) {
        return new ConsoleOperationStatus(id, name, finalStatus, startedAt, finishedAt, failure);
    }
}
