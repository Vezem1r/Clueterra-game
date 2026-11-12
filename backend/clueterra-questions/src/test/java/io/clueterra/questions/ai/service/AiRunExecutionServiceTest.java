package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.mapper.AiRunMapper;
import io.clueterra.questions.ai.mapper.AiStageMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import io.clueterra.questions.ai.model.run.AiStageType;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import io.clueterra.questions.ai.repository.AiRunRepository;
import io.clueterra.questions.ai.service.change.AiChangeSetService;
import io.clueterra.questions.ai.service.generation.AiGenerationQuotaService;
import io.clueterra.questions.ai.service.generation.AiGenerationStageExecutor;
import io.clueterra.questions.ai.service.review.AiReviewQuotaService;
import io.clueterra.questions.ai.service.review.AiReviewStageExecutor;
import io.clueterra.questions.ai.service.run.AiRunExecutionService;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.config.AppSettingsReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRunExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T20:00:00Z");

    @Mock
    private AiRunRepository runRepository;
    @Mock
    private AiExecutionRepository executionRepository;
    @Mock
    private AiReviewQueueRepository reviewQueueRepository;
    @Mock
    private AiReviewQuotaService reviewQuotaService;
    @Mock
    private AiGenerationQuotaService generationQuotaService;
    @Mock
    private AiRunMapper runMapper;
    @Mock
    private AiStageMapper stageMapper;
    @Mock
    private AiStageExecutionSupport stageExecutionSupport;
    @Mock
    private AiReviewStageExecutor reviewStageExecutor;
    @Mock
    private AiGenerationStageExecutor generationStageExecutor;
    @Mock
    private AiChangeSetService changeSetService;
    @Mock
    private AppSettingsReader appSettingsReader;
    @Mock
    private AiReviewQuotaSnapshot quotaSnapshot;

    @Test
    void autoAppliesCompletedReviewBatchesWhenALaterBatchEndsWith503() {
        AiRun preparedRun = run(AiRunStatus.PREPARED);
        AiRun runningRun = run(AiRunStatus.RUNNING);
        AiRun failedRun = run(AiRunStatus.FAILED);
        AiRunStage firstStage = stage(11L, 1, AiStageStatus.PREPARED);
        AiRunStage secondStage = stage(12L, 2, AiStageStatus.PREPARED);
        AiRunStage runningFirstStage = stage(11L, 1, AiStageStatus.RUNNING);
        AiRunStage runningSecondStage = stage(12L, 2, AiStageStatus.RUNNING);
        AiRunStage completedFirstStage = stage(11L, 1, AiStageStatus.COMPLETED);
        AiRunStage failedSecondStage = stage(12L, 2, AiStageStatus.FAILED);
        AiStageExecutionResult firstResult = new AiStageExecutionResult(
                8, 2, 0, 1_000, 200, 0, null, Map.of()
        );
        RuntimeException unavailable = new RuntimeException(
                "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"
        );
        AiChangeSet changeSet = new AiChangeSet(
                91L,
                runningRun.id(),
                AiChangeSetType.REVIEW,
                AiChangeSetStatus.DRAFT,
                "Review",
                "Partial safe review changes",
                2,
                0,
                0,
                Map.of(),
                NOW,
                null,
                null
        );

        when(runRepository.findById(preparedRun.id())).thenReturn(Optional.of(preparedRun));
        when(runRepository.save(any(AiRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.findStagesByRunId(preparedRun.id())).thenReturn(List.of(firstStage, secondStage));
        when(runRepository.saveStages(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runMapper.toRunning(preparedRun)).thenReturn(runningRun);
        when(runMapper.toFailed(
                eq(runningRun), anyInt(), anyInt(), anyInt(), anyLong(), anyLong(), anyInt(), anyString()
        )).thenReturn(failedRun);
        when(stageMapper.toRunning(firstStage)).thenReturn(runningFirstStage);
        when(stageMapper.toRunning(secondStage)).thenReturn(runningSecondStage);
        when(stageMapper.toCompleted(runningFirstStage, firstResult)).thenReturn(completedFirstStage);
        when(stageMapper.toFailed(eq(runningSecondStage), anyString())).thenReturn(failedSecondStage);
        when(reviewStageExecutor.execute(runningRun, runningFirstStage)).thenReturn(firstResult);
        when(reviewStageExecutor.execute(runningRun, runningSecondStage)).thenThrow(unavailable);
        when(stageExecutionSupport.extractLongList(any(), anyString())).thenReturn(List.of());
        when(stageExecutionSupport.summarizeException(unavailable)).thenReturn(unavailable.getMessage());
        when(reviewQuotaService.getSnapshot()).thenReturn(quotaSnapshot);
        when(reviewQuotaService.summarizeSnapshot(quotaSnapshot)).thenReturn("quota");
        when(quotaSnapshot.requestsLastMinuteRemaining()).thenReturn(15);
        when(quotaSnapshot.inputTokensLastMinuteRemaining()).thenReturn(250_000L);
        when(quotaSnapshot.requestsTodayRemaining()).thenReturn(500);
        when(quotaSnapshot.googleSearchEnabled()).thenReturn(false);
        when(appSettingsReader.isAutoApproveReview()).thenReturn(true);
        when(executionRepository.findChangeSetByRunId(runningRun.id())).thenReturn(Optional.of(changeSet));

        service().executeRun(preparedRun.id());

        verify(runRepository).save(failedRun);
        verify(changeSetService).applySafeReviewUpdates(changeSet.id());
    }

    private AiRunExecutionService service() {
        return new AiRunExecutionService(
                runRepository,
                executionRepository,
                reviewQueueRepository,
                reviewQuotaService,
                generationQuotaService,
                runMapper,
                stageMapper,
                stageExecutionSupport,
                reviewStageExecutor,
                generationStageExecutor,
                changeSetService,
                appSettingsReader
        );
    }

    private AiRun run(AiRunStatus status) {
        return new AiRun(
                1L,
                AiRunType.REVIEW,
                AiRunTriggerType.SCHEDULED,
                status,
                "gemini",
                "gemini-3.1-flash-lite",
                "review-v2",
                "scheduler",
                2,
                16,
                0,
                0,
                0,
                2_000,
                400,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                null,
                NOW,
                status == AiRunStatus.PREPARED ? null : NOW,
                status == AiRunStatus.FAILED ? NOW : null
        );
    }

    private AiRunStage stage(Long id, int index, AiStageStatus status) {
        return new AiRunStage(
                id,
                1L,
                index,
                AiStageType.REVIEW_BATCH,
                status,
                8,
                1_000,
                200,
                0,
                0,
                0,
                Map.of("hintIds", List.of(), "countryCodes", List.of()),
                null,
                null,
                null,
                NOW,
                status == AiStageStatus.PREPARED ? null : NOW,
                status == AiStageStatus.COMPLETED || status == AiStageStatus.FAILED ? NOW : null
        );
    }
}
