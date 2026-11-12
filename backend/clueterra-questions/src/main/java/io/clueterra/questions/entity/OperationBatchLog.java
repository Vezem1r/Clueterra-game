package io.clueterra.questions.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationBatchLog {

    private Long id;
    private OperationType operationType;
    private OperationTriggerType triggerType;
    private BatchStatus status;

    @Builder.Default
    private Integer totalItems = 0;

    @Builder.Default
    private Integer successfulItems = 0;

    @Builder.Default
    private Integer failedItems = 0;

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private String errorMessage;
    private Map<String, Object> summary;

    public static OperationBatchLog start(OperationType operationType, OperationTriggerType triggerType) {
        return OperationBatchLog.builder()
                .operationType(operationType)
                .triggerType(triggerType)
                .status(BatchStatus.STARTED)
                .startedAt(Instant.now())
                .totalItems(0)
                .successfulItems(0)
                .failedItems(0)
                .build();
    }

    public void complete() {
        this.status = failedItems > 0 ? BatchStatus.PARTIAL : BatchStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
    }

    public void fail(Exception e) {
        this.status = BatchStatus.FAILED;
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.errorMessage = e.getMessage();
    }

    public void incrementSuccess() {
        this.successfulItems = (this.successfulItems != null ? this.successfulItems : 0) + 1;
    }

    public void addSuccesses(int count) {
        this.successfulItems = (this.successfulItems != null ? this.successfulItems : 0) + count;
    }

    public void incrementFailed() {
        this.failedItems = (this.failedItems != null ? this.failedItems : 0) + 1;
    }

    public void setTotalItemsCount(int count) {
        this.totalItems = count;
    }
}
