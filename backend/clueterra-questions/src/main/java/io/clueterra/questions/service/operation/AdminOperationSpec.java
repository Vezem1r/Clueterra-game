package io.clueterra.questions.service.operation;

import io.clueterra.questions.entity.OperationType;

public record AdminOperationSpec(String idPrefix, String displayName, OperationType operationType, String mode) {
}
