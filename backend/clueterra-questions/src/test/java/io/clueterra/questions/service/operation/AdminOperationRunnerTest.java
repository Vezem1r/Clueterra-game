package io.clueterra.questions.service.operation;

import io.clueterra.questions.entity.BatchStatus;
import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.exception.OperationInProgressException;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOperationRunnerTest {

    private static final AdminOperationSpec SPEC =
            new AdminOperationSpec("test_op", "Test Operation", OperationType.DATA_SYNC, "TEST");

    private final ConsoleLogWriter console = mock(ConsoleLogWriter.class);
    private final OperationBatchLogRepository batchLogRepository = mock(OperationBatchLogRepository.class);
    private final AdminOperationRunner runner =
            new AdminOperationRunner(console, batchLogRepository, new TaskExecutorAdapter(Runnable::run));

    @Test
    void completesOwnOperationAndStoresSummary() {
        when(batchLogRepository.save(any(OperationBatchLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String operationId = runner.launch(SPEC, context -> OperationSummary.of("processed", 3));

        assertThat(operationId).startsWith("test_op-");
        verify(console).startOperation(operationId, "Test Operation");
        verify(console).completeOperation(operationId);
        ArgumentCaptor<OperationBatchLog> captor = ArgumentCaptor.forClass(OperationBatchLog.class);
        verify(batchLogRepository).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(captor.getValue().getSummary())
                .containsEntry("operationId", operationId)
                .containsEntry("mode", "TEST")
                .containsEntry("processed", 3);
    }

    @Test
    void failsOwnOperationWhenTaskThrows() {
        when(batchLogRepository.save(any(OperationBatchLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String operationId = runner.launch(SPEC, context -> {
            throw new IllegalStateException("boom");
        });

        verify(console).failOperation(operationId, "boom");
        verify(console, never()).completeOperation(anyString());
        ArgumentCaptor<OperationBatchLog> captor = ArgumentCaptor.forClass(OperationBatchLog.class);
        verify(batchLogRepository).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    void refusesToLaunchWhileAnotherOperationRuns() {
        doThrow(new OperationInProgressException("Full Sync"))
                .when(console).startOperation(startsWith("test_op-"), any());

        assertThatThrownBy(() -> runner.launch(SPEC, context -> OperationSummary.empty()))
                .isInstanceOf(OperationInProgressException.class);
        verify(batchLogRepository, never()).save(any());
    }
}
