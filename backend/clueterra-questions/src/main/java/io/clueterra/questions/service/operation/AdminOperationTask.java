package io.clueterra.questions.service.operation;

@FunctionalInterface
public interface AdminOperationTask {

    OperationSummary run(AdminOperationContext context) throws Exception;
}
