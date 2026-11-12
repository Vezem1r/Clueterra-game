package io.clueterra.api.admin.console.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import io.clueterra.api.admin.console.dto.ConsoleLogEntry;
import io.clueterra.api.admin.console.dto.ConsoleStateDto;
import io.clueterra.api.admin.console.dto.OperationStatusDto;
import io.clueterra.api.admin.console.config.ConsoleProperties;
import io.clueterra.api.common.utils.redis.RedisHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsoleLogService {

    private final RedisHelper redis;
    private final ConsoleProperties properties;

    private static final TypeReference<List<ConsoleLogEntry>> LIST_REF = new TypeReference<>() {
    };

    public List<ConsoleLogEntry> getLogs(ConsoleChannel channel) {
        List<ConsoleLogEntry> entries = redis.read(channel.logsKey(), LIST_REF)
                .orElseGet(Collections::emptyList);
        long cutoff = System.currentTimeMillis() - properties.ttl().toMillis();
        List<ConsoleLogEntry> pruned = entries.stream()
                .filter(entry -> entry.timestamp() > cutoff)
                .toList();
        if (pruned.size() < entries.size()) {
            write(channel, pruned);
        }
        return pruned;
    }

    public ConsoleStateDto getState(ConsoleChannel channel) {
        return new ConsoleStateDto(readOperationStatus(channel), getLogs(channel));
    }

    public void clearOperationStatus(ConsoleChannel channel) {
        redis.delete(channel.operationKey());
    }

    public void clear(ConsoleChannel channel) {
        redis.delete(channel.logsKey());
    }

    private OperationStatusDto readOperationStatus(ConsoleChannel channel) {
        return redis.read(channel.operationKey(), OperationStatusDto.class).orElse(null);
    }

    private void write(ConsoleChannel channel, List<ConsoleLogEntry> entries) {
        List<ConsoleLogEntry> trimmed = entries.size() > properties.maxEntries()
                ? entries.subList(entries.size() - properties.maxEntries(), entries.size())
                : entries;
        redis.write(channel.logsKey(), trimmed, properties.ttl());
    }
}
