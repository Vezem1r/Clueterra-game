package io.clueterra.questions.ai.service.review;

import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.assistant.AiHintReviewAssistant;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.mapper.AiChangeMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import io.clueterra.questions.ai.model.review.AiHintReviewBatchResult;
import io.clueterra.questions.ai.model.review.AiHintReviewItemResult;
import io.clueterra.questions.ai.model.review.AiHintReviewResult;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewDecision;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import io.clueterra.questions.ai.service.change.AiChangeSnapshotFactory;
import io.clueterra.questions.ai.service.generation.AiGenerationHintHeuristics;
import io.clueterra.questions.ai.service.support.AiProviderDispatcher;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.ai.service.support.AiStructuredOutputParser;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.HintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewStageExecutorTest {

    private static final Instant NOW = Instant.parse("2026-10-12T20:00:00Z");

    @Mock private AiReviewQueueRepository reviewQueueRepository;
    @Mock private AiHintReviewAssistant hintReviewAssistant;
    @Mock private AiReviewQuotaService reviewQuotaService;
    @Mock private AiProviderDispatcher providerDispatcher;
    @Mock private AiExecutionRepository executionRepository;
    @Mock private AiStructuredOutputParser structuredOutputParser;
    @Mock private AiChangeSnapshotFactory snapshotFactory;
    @Mock private AiStageExecutionSupport stageExecutionSupport;
    @Mock private HintRepository hintRepository;
    @Mock private CountryRepository countryRepository;
    @Mock private AiGenerationHintHeuristics heuristics;
    @Mock private AiReviewQualityAnalyzer qualityAnalyzer;
    @Mock private AiRun run;
    @Mock private AiRunStage stage;
    @Mock private Result<String> modelResult;

    @Test
    void answerLeakingEditIsRejectedAndHintReturnsToReviewQueue() {
        AiProperties properties = new AiProperties();
        properties.getReview().getTokenization().setAllowImages(false);
        AiReviewEditValidator editValidator = new AiReviewEditValidator(Optional.empty());
        AiReviewStageExecutor executor = new AiReviewStageExecutor(
                properties, reviewQueueRepository, hintReviewAssistant, reviewQuotaService,
                providerDispatcher, executionRepository, structuredOutputParser, snapshotFactory,
                new AiChangeMapper(Clock.fixed(NOW, ZoneOffset.UTC)), stageExecutionSupport,
                hintRepository, countryRepository, heuristics, editValidator,
                new AiReviewDecisionPolicy(qualityAnalyzer), qualityAnalyzer, Optional.empty(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        AiReviewCandidate candidate = new AiReviewCandidate(
                1L, 2L, "NZ", "New Zealand", "Oceania", "Australia and New Zealand", "Wellington",
                BigDecimal.ONE, "This country has an aging population.", null, null, 50,
                BigDecimal.valueOf(0.8), DataSource.CIA_FACTBOOK, "CIA_TEST", 2026,
                "revision", HintReviewStatus.IN_REVIEW, NOW, null);
        Hint hint = Hint.builder().id(1L).countryId(2L).hintText(candidate.hintText()).build();
        AiHintReviewItemResult itemResult = new AiHintReviewItemResult(
                1L, "EDIT_TEXT", "CONSISTENT", "STRONG", "IMPROVABLE", "OK", "TEXT_QUALITY",
                0.85, "Improve text", "difficulty: ok", "New Zealand has an aging population.",
                null, null, null);
        AiChangeSet changeSet = new AiChangeSet(3L, 10L, AiChangeSetType.REVIEW,
                AiChangeSetStatus.DRAFT, "Review", "Review", 0, 0, 0, Map.of(), NOW, null, null);
        when(run.id()).thenReturn(10L);
        when(run.runType()).thenReturn(AiRunType.REVIEW);
        when(stage.id()).thenReturn(11L);
        when(stageExecutionSupport.extractLongList(any(), eq("hintIds"))).thenReturn(List.of(1L));
        when(stageExecutionSupport.toJson(any())).thenReturn("{}");
        when(reviewQueueRepository.findByHintIds(List.of(1L))).thenReturn(List.of(candidate));
        when(qualityAnalyzer.analyze(candidate)).thenReturn(List.of());
        when(providerDispatcher.dispatch(any())).thenReturn(modelResult);
        when(structuredOutputParser.parseReviewBatchResult(modelResult))
                .thenReturn(new AiHintReviewBatchResult("Reviewed", List.of(itemResult)));
        when(hintRepository.findByIds(List.of(1L))).thenReturn(List.of(hint));
        when(countryRepository.findById(2L)).thenReturn(Optional.of(Country.builder().id(2L).build()));
        when(executionRepository.findChangeSetByRunId(10L)).thenReturn(Optional.empty());
        when(executionRepository.saveChangeSet(any())).thenReturn(changeSet);
        when(snapshotFactory.snapshotHint(any(), any())).thenReturn(Map.of("hintText", candidate.hintText()));
        when(snapshotFactory.snapshotReviewProposal(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("hintText", itemResult.proposedText()));

        AiStageExecutionResult result = executor.execute(run, stage);

        ArgumentCaptor<AiChangeItem> changeItem = ArgumentCaptor.forClass(AiChangeItem.class);
        verify(executionRepository).saveChangeItem(changeItem.capture());
        assertThat(changeItem.getValue().status()).isEqualTo(AiChangeItemStatus.REJECTED);
        assertThat(changeItem.getValue().proposedSnapshot()).containsEntry("autoRejected", true);
        ArgumentCaptor<AiHintReviewResult> reviewResult = ArgumentCaptor.forClass(AiHintReviewResult.class);
        verify(executionRepository).saveHintReviewResult(reviewResult.capture());
        assertThat(reviewResult.getValue().decision()).isEqualTo(AiReviewDecision.ERROR);
        verify(hintRepository).markReviewFailed(1L, 10L, NOW);
        verify(hintRepository, never()).markReviewActionPending(1L, 10L, NOW);
        assertThat(result.resultSummary()).containsEntry("decisionCounts", Map.of("REJECTED_ANSWER_LEAK", 1));

        ArgumentCaptor<Map<String, Object>> prompt = ArgumentCaptor.forClass(Map.class);
        verify(stageExecutionSupport).toJson(prompt.capture());
        Map<?, ?> promptHint = (Map<?, ?>) ((List<?>) prompt.getValue().get("hints")).getFirst();
        Map<?, ?> allowedContext = (Map<?, ?>) promptHint.get("allowedContext");
        assertThat(allowedContext.get("region")).isEqualTo("Oceania");
        assertThat(allowedContext.get("subregion")).isNull();
    }
}
