package io.clueterra.questions.service.console;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.exception.OperationInProgressException;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsoleLogWriter {

    private static final String CONSOLE_KEY = "admin:console:questions:logs";
    private static final String OPERATION_KEY = "admin:console:questions:operation";
    private static final String OPERATION_LOCK_KEY = "admin:console:questions:operation:lock";
    private static final int MAX_ENTRIES = 3000;
    private static final Duration TTL = Duration.ofDays(3);
    private static final Duration OPERATION_TTL = Duration.ofHours(24);
    private static final Duration OPERATION_LOCK_TTL = Duration.ofHours(12);
    private static final String RESTART_INTERRUPTION_ERROR = "Operation was interrupted because the Questions service restarted";

    private static final RedisScript<Long> APPEND_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            local entries = {}
            if raw then entries = cjson.decode(raw) end
            table.insert(entries, cjson.decode(ARGV[1]))
            local max = tonumber(ARGV[2])
            local count = #entries
            if count > max then
              local trimmed = {}
              for i = count - max + 1, count do table.insert(trimmed, entries[i]) end
              entries = trimmed
            end
            redis.call('SET', KEYS[1], cjson.encode(entries), 'PX', ARGV[3])
            return #entries
            """, Long.class);

    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final OperationBatchLogRepository operationBatchLogRepository;

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

    private void append(String text, String type) {
        try {
            String entry = mapper.writeValueAsString(new ConsoleEntry(text, type, System.currentTimeMillis()));
            redis.execute(APPEND_SCRIPT, List.of(CONSOLE_KEY),
                    entry, String.valueOf(MAX_ENTRIES), String.valueOf(TTL.toMillis()));
        } catch (Exception e) {
            log.warn("Console log write failed", e);
        }
    }

    public boolean isOperationRunning() {
        return redis.hasKey(OPERATION_LOCK_KEY);
    }

    public void startOperation(String id, String name) {
        Boolean acquired = redis.opsForValue().setIfAbsent(OPERATION_LOCK_KEY, id, OPERATION_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            String running = readStatus().map(ConsoleOperationStatus::name).orElse("unknown");
            throw new OperationInProgressException(running);
        }
        writeStatus(new ConsoleOperationStatus(
                id, name, ConsoleOperationStatus.RUNNING, System.currentTimeMillis(), null, null));
    }

    public void completeOperation(String id) {
        finishOperation(id, ConsoleOperationStatus.COMPLETED, null);
    }

    public void failOperation(String id, String error) {
        finishOperation(id, ConsoleOperationStatus.FAILED, error);
    }

    private void finishOperation(String id, String finalStatus, String error) {
        try {
            readStatus()
                    .filter(current -> current.id().equals(id))
                    .ifPresentOrElse(
                            current -> writeStatus(current.finish(finalStatus, System.currentTimeMillis(), error)),
                            () -> log.warn("Operation {} finished as {} but is no longer the current operation", id, finalStatus));
        } catch (Exception e) {
            log.warn("Operation status update failed for {}", id, e);
        } finally {
            redis.execute(RELEASE_LOCK_SCRIPT, List.of(OPERATION_LOCK_KEY), id);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedOperationAfterRestart() {
        try {
            Optional<ConsoleOperationStatus> running = readStatus().filter(ConsoleOperationStatus::isRunning);
            redis.delete(OPERATION_LOCK_KEY);
            if (running.isEmpty()) {
                return;
            }
            ConsoleOperationStatus current = running.get();
            long completedAt = System.currentTimeMillis();
            writeStatus(current.finish(ConsoleOperationStatus.FAILED, completedAt, RESTART_INTERRUPTION_ERROR));

            int recoveredLogs = operationBatchLogRepository.markInterrupted(
                    current.id(),
                    Instant.ofEpochMilli(completedAt),
                    RESTART_INTERRUPTION_ERROR);
            error("Previous operation '" + current.name() + "' was interrupted by a service restart.");
            log.warn("Recovered interrupted console operation {} after restart; operation logs updated: {}",
                    current.id(), recoveredLogs);
        } catch (Exception e) {
            log.warn("Interrupted operation recovery failed", e);
        }
    }

    private Optional<ConsoleOperationStatus> readStatus() {
        String json = redis.opsForValue().get(OPERATION_KEY);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, ConsoleOperationStatus.class));
        } catch (JsonProcessingException e) {
            log.warn("Unreadable operation status in Redis", e);
            return Optional.empty();
        }
    }

    private void writeStatus(ConsoleOperationStatus status) {
        try {
            redis.opsForValue().set(OPERATION_KEY, mapper.writeValueAsString(status), OPERATION_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize operation status", e);
        }
    }
}
