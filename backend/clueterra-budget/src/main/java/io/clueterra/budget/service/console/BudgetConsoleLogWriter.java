package io.clueterra.budget.service.console;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import io.clueterra.budget.common.redis.RedisHelper;
import io.clueterra.budget.config.BudgetSyncProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetConsoleLogWriter {

    private static final String CONSOLE_KEY = "admin:console:budget:logs";
    private static final String OPERATION_KEY = "admin:console:budget:operation";
    private static final String OPERATION_LOCK_KEY = "admin:console:budget:operation:lock";
    private static final int MAX_ENTRIES = 3000;
    private static final Duration LOG_TTL = Duration.ofDays(3);
    private static final Duration FINISHED_OPERATION_TTL = Duration.ofHours(24);
    private static final TypeReference<List<BudgetConsoleEntry>> LOG_LIST_TYPE =
            new TypeReference<>() {
            };

    private final RedisHelper redis;
    private final BudgetSyncProperties syncProperties;

    public void info(String text) {
        append(text, "info");
    }

    public void success(String text) {
        append(text, "success");
    }

    public void error(String text) {
        append(text, "error");
    }

    public void system(String text) {
        append(text, "system");
    }

    public boolean tryStartOperation(String id, String name) {
        Duration leaseTtl = syncProperties.staleRunTimeout();
        if (!redis.writeIfAbsent(OPERATION_LOCK_KEY, id, leaseTtl)) {
            return false;
        }
        try {
            redis.writeRequired(
                    OPERATION_KEY,
                    new BudgetOperationStatus(id, name, "RUNNING", System.currentTimeMillis(), null, null),
                    leaseTtl);
            return true;
        } catch (RuntimeException exception) {
            redis.deleteIfValueMatches(OPERATION_LOCK_KEY, id);
            throw exception;
        }
    }

    public void heartbeatOperation(String operationId) {
        if (!redis.renewLease(
                OPERATION_LOCK_KEY,
                OPERATION_KEY,
                operationId,
                syncProperties.staleRunTimeout())) {
            throw new IllegalStateException(
                    "Budget operation lease expired or ownership changed: " + operationId);
        }
    }

    public void completeOperation(String operationId) {
        finishOperation(operationId, "COMPLETED", null);
    }

    public void failOperation(String operationId, String failure) {
        finishOperation(operationId, "FAILED", failure);
    }

    private void finishOperation(String operationId, String status, String failure) {
        if (!redis.renewLease(
                OPERATION_LOCK_KEY,
                OPERATION_KEY,
                operationId,
                syncProperties.staleRunTimeout())) {
            return;
        }
        redis
                .read(OPERATION_KEY, BudgetOperationStatus.class)
                .filter(current -> operationId.equals(current.id()))
                .ifPresent(
                        current -> {
                            redis.writeRequired(
                                    OPERATION_KEY,
                                    new BudgetOperationStatus(
                                            current.id(),
                                            current.name(),
                                            status,
                                            current.startedAt(),
                                            System.currentTimeMillis(),
                                            failure),
                                    FINISHED_OPERATION_TTL);
                            redis.deleteIfValueMatches(OPERATION_LOCK_KEY, operationId);
                        });
    }

    private void append(String text, String type) {
        List<BudgetConsoleEntry> entries =
                new ArrayList<>(redis.read(CONSOLE_KEY, LOG_LIST_TYPE).orElse(List.of()));
        entries.add(new BudgetConsoleEntry(text, type, System.currentTimeMillis()));
        List<BudgetConsoleEntry> trimmed =
                entries.size() > MAX_ENTRIES
                        ? List.copyOf(entries.subList(entries.size() - MAX_ENTRIES, entries.size()))
                        : List.copyOf(entries);
        redis.write(CONSOLE_KEY, trimmed, LOG_TTL);
    }
}
