package io.clueterra.questions.service.operation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.entity.OperationTriggerType;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AdminOperationRunner {

    private final ConsoleLogWriter console;
    private final OperationBatchLogRepository batchLogRepository;
    private final AsyncTaskExecutor executor;

    public AdminOperationRunner(
            ConsoleLogWriter console,
            OperationBatchLogRepository batchLogRepository,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.console = console;
        this.batchLogRepository = batchLogRepository;
        this.executor = executor;
    }

    public String launch(AdminOperationSpec spec, AdminOperationTask task) {
        String operationId = spec.idPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
        console.startOperation(operationId, spec.displayName());
        OperationBatchLog batchLog;
        try {
            batchLog = OperationBatchLog.start(spec.operationType(), OperationTriggerType.MANUAL);
            batchLog.setSummary(baseSummary(operationId, spec));
            batchLog = batchLogRepository.save(batchLog);
        } catch (RuntimeException e) {
            console.failOperation(operationId, "Could not record operation start: " + e.getMessage());
            throw e;
        }

        AdminOperationContext context = new AdminOperationContext(operationId, batchLog);
        log.info("[op={}] Starting {}", operationId, spec.displayName());
        executor.execute(() -> execute(spec, task, context));
        return operationId;
    }

    private void execute(AdminOperationSpec spec, AdminOperationTask task, AdminOperationContext context) {
        OperationBatchLog batchLog = context.batchLog();
        try {
            OperationSummary summary = task.run(context);
            Map<String, Object> stored = baseSummary(context.operationId(), spec);
            stored.putAll(summary.asMap());
            batchLog.complete();
            batchLog.setSummary(stored);
            batchLogRepository.update(batchLog);
            log.info("[op={}] {} completed", context.operationId(), spec.displayName());
            console.completeOperation(context.operationId());
        } catch (Exception e) {
            String message = conciseErrorMessage(e);
            log.error("[op={}] {} failed: {}", context.operationId(), spec.displayName(), message, e);
            try {
                batchLog.fail(e);
                batchLog.setSummary(baseSummary(context.operationId(), spec));
                batchLogRepository.update(batchLog);
            } catch (RuntimeException logFailure) {
                log.error("[op={}] Could not record operation failure", context.operationId(), logFailure);
            }
            console.error(spec.displayName() + " failed: " + message);
            console.failOperation(context.operationId(), message);
        }
    }

    private Map<String, Object> baseSummary(String operationId, AdminOperationSpec spec) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("operationId", operationId);
        summary.put("mode", spec.mode());
        return summary;
    }

    public static String conciseErrorMessage(Throwable throwable) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(throwable);
        String message = root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage()
                : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 240 ? message.substring(0, 237) + "..." : message;
    }
}
