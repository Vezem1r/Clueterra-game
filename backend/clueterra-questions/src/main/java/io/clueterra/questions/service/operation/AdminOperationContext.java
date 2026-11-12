package io.clueterra.questions.service.operation;

import io.clueterra.questions.entity.OperationBatchLog;

public record AdminOperationContext(String operationId, OperationBatchLog batchLog) {
}
