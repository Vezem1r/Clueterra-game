package io.clueterra.api.admin.console.dto;

import java.util.List;

public record ConsoleStateDto(
        OperationStatusDto operation,
        List<ConsoleLogEntry> logs) {
}
