package io.clueterra.budget.operation;

import java.util.Locale;
import java.util.UUID;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import io.clueterra.budget.controller.dto.BudgetOperationAcceptedResponse;
import io.clueterra.budget.exception.SyncAlreadyRunningException;
import io.clueterra.budget.service.console.BudgetConsoleLogWriter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetAdminOperationService {

    private final BudgetConsoleLogWriter console;
    private final BudgetAdminOperationRunner runner;
    private final JobScheduler jobScheduler;

    public BudgetOperationAcceptedResponse start(BudgetAdminOperationType operationType) {
        String operationId =
                "budget-"
                        + operationType.name().toLowerCase(Locale.ROOT).replace('_', '-')
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8);
        if (!console.tryStartOperation(operationId, operationType.displayName())) {
            throw new SyncAlreadyRunningException("Another BudgetGuessr operation is already running");
        }
        console.system("Starting " + operationType.displayName() + "...");
        try {
            jobScheduler.enqueue(() -> runner.run(operationId, operationType));
        } catch (RuntimeException exception) {
            console.failOperation(operationId, "Unable to schedule operation");
            throw exception;
        }
        return new BudgetOperationAcceptedResponse(
                operationId, operationType.displayName(), "ACCEPTED");
    }
}
