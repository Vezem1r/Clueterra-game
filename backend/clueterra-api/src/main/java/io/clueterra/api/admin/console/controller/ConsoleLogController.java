package io.clueterra.api.admin.console.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.console.dto.ConsoleLogEntry;
import io.clueterra.api.admin.console.dto.ConsoleStateDto;
import io.clueterra.api.admin.console.service.ConsoleChannel;
import io.clueterra.api.admin.console.service.ConsoleLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/console")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Console", description = "Admin console log persistence")
public class ConsoleLogController {

    private final ConsoleLogService logService;

    @GetMapping("/state")
    @Operation(summary = "Get console state for an admin service")
    public ResponseEntity<ConsoleStateDto> getState(
            @RequestParam(defaultValue = "QUESTIONS") ConsoleChannel service) {
        return ResponseEntity.ok(logService.getState(service));
    }

    @GetMapping("/logs")
    @Operation(summary = "Get console logs for an admin service")
    public ResponseEntity<List<ConsoleLogEntry>> getLogs(
            @RequestParam(defaultValue = "QUESTIONS") ConsoleChannel service) {
        return ResponseEntity.ok(logService.getLogs(service));
    }

    @DeleteMapping("/logs")
    @Operation(summary = "Clear console logs for an admin service")
    public ResponseEntity<Void> clear(
            @RequestParam(defaultValue = "QUESTIONS") ConsoleChannel service) {
        logService.clear(service);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/operation")
    @Operation(summary = "Clear operation status for an admin service")
    public ResponseEntity<Void> clearOperation(
            @RequestParam(defaultValue = "QUESTIONS") ConsoleChannel service) {
        logService.clearOperationStatus(service);
        return ResponseEntity.ok().build();
    }
}
