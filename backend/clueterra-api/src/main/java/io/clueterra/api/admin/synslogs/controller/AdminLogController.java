package io.clueterra.api.admin.synslogs.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.synslogs.dto.DataSyncLogAdminDto;
import io.clueterra.api.admin.synslogs.dto.GroupedErrorDto;
import io.clueterra.api.admin.synslogs.dto.HintGenerationLogAdminDto;
import io.clueterra.api.admin.synslogs.dto.OperationBatchLogAdminDto;
import io.clueterra.api.admin.synslogs.service.LogService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Logs", description = "System logs")
public class AdminLogController {

    private final LogService logService;

    @GetMapping("/operations")
    public ResponseEntity<Page<OperationBatchLogAdminDto>> getOperations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String startAfter,
            @RequestParam(required = false) String startBefore,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(logService.listOperations(page, size, operationType, status, triggerType, startAfter, startBefore, sortBy, sortDir));
    }

    @GetMapping("/operations/types")
    public ResponseEntity<List<String>> getOperationTypes() {
        return ResponseEntity.ok(logService.getOperationTypes());
    }

    @GetMapping("/operations/statuses")
    public ResponseEntity<List<String>> getOperationStatuses() {
        return ResponseEntity.ok(logService.getOperationStatuses());
    }

    @GetMapping("/operations/trigger-types")
    public ResponseEntity<List<String>> getOperationTriggerTypes() {
        return ResponseEntity.ok(logService.getOperationTriggerTypes());
    }

    @GetMapping("/sync")
    public ResponseEntity<Page<DataSyncLogAdminDto>> getSyncLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String syncType,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String startAfter,
            @RequestParam(required = false) String startBefore,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(logService.listSyncLogs(page, size, source, status, syncType, countryCode, startAfter, startBefore, sortBy, sortDir));
    }

    @GetMapping("/sync/sources")
    public ResponseEntity<List<String>> getSyncSources() {
        return ResponseEntity.ok(logService.getSyncSources());
    }

    @GetMapping("/sync/statuses")
    public ResponseEntity<List<String>> getSyncStatuses() {
        return ResponseEntity.ok(logService.getSyncStatuses());
    }

    @GetMapping("/sync/types")
    public ResponseEntity<List<String>> getSyncTypes() {
        return ResponseEntity.ok(logService.getSyncTypes());
    }

    @GetMapping("/hints")
    public ResponseEntity<Page<HintGenerationLogAdminDto>> getHintLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String startAfter,
            @RequestParam(required = false) String startBefore,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(logService.listHintLogs(page, size, countryCode, triggerType, startAfter, startBefore, sortBy, sortDir));
    }

    @GetMapping("/hints/trigger-types")
    public ResponseEntity<List<String>> getHintTriggerTypes() {
        return ResponseEntity.ok(logService.getHintTriggerTypes());
    }

    @GetMapping("/grouped-errors")
    public ResponseEntity<List<GroupedErrorDto>> getGroupedErrors(
            @RequestParam(defaultValue = "operations") String logType,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(logService.getGroupedErrors(logType, limit));
    }
}
