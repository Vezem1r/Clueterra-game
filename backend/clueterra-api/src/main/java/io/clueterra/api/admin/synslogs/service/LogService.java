package io.clueterra.api.admin.synslogs.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import io.clueterra.api.admin.synslogs.dto.DataSyncLogAdminDto;
import io.clueterra.api.admin.synslogs.dto.GroupedErrorDto;
import io.clueterra.api.admin.synslogs.dto.HintGenerationLogAdminDto;
import io.clueterra.api.admin.synslogs.dto.OperationBatchLogAdminDto;
import io.clueterra.api.admin.synslogs.repository.DataSyncLogJdbcRepository;
import io.clueterra.api.admin.synslogs.repository.HintGenerationLogJdbcRepository;
import io.clueterra.api.admin.synslogs.repository.OperationBatchLogJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogService {

    private final OperationBatchLogJdbcRepository operationsRepo;
    private final DataSyncLogJdbcRepository syncRepo;
    private final HintGenerationLogJdbcRepository hintRepo;

    public Page<OperationBatchLogAdminDto> listOperations(int page, int size, String operationType,
                                                          String status, String triggerType,
                                                          String startAfter, String startBefore,
                                                          String sortBy, String sortDir) {
        return operationsRepo.findAll(operationType, status, triggerType, startAfter, startBefore, sortBy, sortDir, PageRequest.of(page, size));
    }

    public Page<DataSyncLogAdminDto> listSyncLogs(int page, int size, String source,
                                                  String status, String syncType,
                                                  String countryCode, String startAfter,
                                                  String startBefore, String sortBy,
                                                  String sortDir) {
        return syncRepo.findAll(source, status, syncType, countryCode, startAfter, startBefore, sortBy, sortDir, PageRequest.of(page, size));
    }

    public Page<HintGenerationLogAdminDto> listHintLogs(int page, int size, String countryCode,
                                                        String triggerType, String startAfter,
                                                        String startBefore, String sortBy,
                                                        String sortDir) {
        return hintRepo.findAll(countryCode, triggerType, startAfter, startBefore, sortBy, sortDir, PageRequest.of(page, size));
    }

    public List<GroupedErrorDto> getGroupedErrors(String logType, int limit) {
        return switch (logType) {
            case "operations" -> operationsRepo.findGroupedErrors(limit);
            case "sync" -> syncRepo.findGroupedErrors(limit);
            case "hints" -> hintRepo.findGroupedErrors(limit);
            default -> List.of();
        };
    }

    public List<String> getOperationTypes() {
        return operationsRepo.findDistinctTypes();
    }

    public List<String> getOperationStatuses() {
        return operationsRepo.findDistinctStatuses();
    }

    public List<String> getOperationTriggerTypes() {
        return operationsRepo.findDistinctTriggerTypes();
    }

    public List<String> getSyncSources() {
        return syncRepo.findDistinctSources();
    }

    public List<String> getSyncStatuses() {
        return syncRepo.findDistinctStatuses();
    }

    public List<String> getSyncTypes() {
        return syncRepo.findDistinctTypes();
    }

    public List<String> getHintTriggerTypes() {
        return hintRepo.findDistinctTriggerTypes();
    }
}
