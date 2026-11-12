package io.clueterra.questions.controller.dto;

public record OperationStartedResponse(String operationId, String status) {

    public static OperationStartedResponse started(String operationId) {
        return new OperationStartedResponse(operationId, "STARTED");
    }
}
