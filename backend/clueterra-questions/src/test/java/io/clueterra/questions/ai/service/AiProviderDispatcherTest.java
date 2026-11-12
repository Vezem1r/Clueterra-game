package io.clueterra.questions.ai.service;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.AiProviderRetryProperties;
import io.clueterra.questions.ai.mapper.AiUsageMapper;
import io.clueterra.questions.ai.model.dispatch.AiProviderDispatchRequest;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.service.support.AiProviderDispatcher;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderDispatcherTest {

    @Mock
    private AiStageExecutionSupport stageExecutionSupport;
    @Mock
    private AiUsageMapper usageMapper;
    @Mock
    private AiExecutionRepository executionRepository;
    @Mock
    private AiRun run;
    @Mock
    private AiRunStage stage;
    @Mock
    private Result<String> successfulResult;
    @Mock
    private Supplier<Result<String>> providerCall;

    private AiProviderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        AiProviderRetryProperties retry = new AiProviderRetryProperties();
        retry.setBackoffMillis(List.of(0L, 0L));
        properties.setProviderRetry(retry);
        dispatcher = new AiProviderDispatcher(
                properties,
                stageExecutionSupport,
                usageMapper,
                executionRepository
        );
    }

    @Test
    void usesSharedThirtySixtyTwoMinuteFiveMinuteScheduleByDefault() {
        assertThat(new AiProviderRetryProperties().getBackoffMillis())
                .containsExactly(30_000L, 60_000L, 120_000L, 300_000L);
    }

    @Test
    void retriesTemporary503AndContinuesTheSameRequest() {
        RuntimeException unavailable = new RuntimeException(
                "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"
        );
        when(stageExecutionSupport.summarizeException(unavailable)).thenReturn(unavailable.getMessage());
        when(providerCall.get()).thenThrow(unavailable).thenReturn(successfulResult);

        Result<String> result = dispatcher.dispatch(request(() -> true));

        assertThat(result).isSameAs(successfulResult);
        verify(providerCall, times(2)).get();
        verify(executionRepository, times(2)).saveRunUsage(null);
    }

    @Test
    void doesNotRetryPermanentClientError() {
        RuntimeException invalidRequest = new RuntimeException(
                "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\"}}"
        );
        when(stageExecutionSupport.summarizeException(invalidRequest)).thenReturn(invalidRequest.getMessage());
        when(providerCall.get()).thenThrow(invalidRequest);

        assertThatThrownBy(() -> dispatcher.dispatch(request(() -> true)))
                .isSameAs(invalidRequest);

        verify(providerCall).get();
        verify(executionRepository).saveRunUsage(null);
    }

    @Test
    void doesNotRetryExhaustedGeminiQuota() {
        RuntimeException exhausted = new RuntimeException(
                "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Rate limit temporarily unavailable\"}}"
        );
        when(stageExecutionSupport.summarizeException(exhausted)).thenReturn(exhausted.getMessage());
        when(providerCall.get()).thenThrow(exhausted);

        assertThatThrownBy(() -> dispatcher.dispatch(request(() -> true)))
                .isSameAs(exhausted);

        verify(providerCall).get();
        verify(executionRepository).saveRunUsage(null);
    }

    @Test
    void doesNotRetrySummarizedQuotaError() {
        RuntimeException exhausted = new RuntimeException("Gemini quota exhausted (429 RESOURCE_EXHAUSTED)");
        when(stageExecutionSupport.summarizeException(exhausted)).thenReturn(exhausted.getMessage());
        when(providerCall.get()).thenThrow(exhausted);

        assertThatThrownBy(() -> dispatcher.dispatch(request(() -> true)))
                .isSameAs(exhausted);

        verify(providerCall).get();
        verify(executionRepository).saveRunUsage(null);
    }

    @Test
    void doesNotSpendAnotherRequestWhenRetryQuotaIsExhausted() {
        RuntimeException unavailable = new RuntimeException(
                "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"
        );
        when(stageExecutionSupport.summarizeException(unavailable)).thenReturn(unavailable.getMessage());
        when(providerCall.get()).thenThrow(unavailable);

        assertThatThrownBy(() -> dispatcher.dispatch(request(() -> false)))
                .isSameAs(unavailable);

        verify(providerCall).get();
        verify(executionRepository).saveRunUsage(null);
    }

    private AiProviderDispatchRequest request(BooleanSupplier retryAllowed) {
        return new AiProviderDispatchRequest(
                run,
                stage,
                new AtomicInteger(1),
                List.<Content>of(),
                true,
                false,
                () -> {
                },
                retryAllowed,
                providerCall
        );
    }
}
