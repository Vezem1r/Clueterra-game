package io.clueterra.questions.ai.service.support;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.mapper.AiUsageMapper;
import io.clueterra.questions.ai.model.dispatch.AiProviderDispatchRequest;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiProviderDispatcher {

    private static final long SLEEP_CHUNK_MILLIS = 30_000L;
    private static final Pattern TRANSIENT_HTTP_STATUS = Pattern.compile(
            "\\\"code\\\"\\s*:\\s*(408|500|502|503|504)"
    );
    private static final Pattern QUOTA_HTTP_STATUS = Pattern.compile(
            "\\\"code\\\"\\s*:\\s*429\\b"
    );

    private final AiProperties aiProperties;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final AiUsageMapper usageMapper;
    private final AiExecutionRepository executionRepository;

    public AiProviderDispatcher(
            AiProperties aiProperties,
            AiStageExecutionSupport stageExecutionSupport,
            AiUsageMapper usageMapper,
            AiExecutionRepository executionRepository) {
        this.aiProperties = aiProperties;
        this.stageExecutionSupport = stageExecutionSupport;
        this.usageMapper = usageMapper;
        this.executionRepository = executionRepository;
    }

    public Result<String> dispatch(AiProviderDispatchRequest request) {
        List<Long> retryDelays = aiProperties.getProviderRetry().getBackoffMillis().stream()
                .map(delay -> Math.max(0L, delay))
                .toList();

        for (int attempt = 0; ; attempt++) {
            request.beforeAttempt().run();
            int currentRequestIndex = request.requestIndex().getAndIncrement();
            long started = System.nanoTime();
            try {
                Result<String> result = request.providerCall().get();
                long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                executionRepository.saveRunUsage(usageMapper.toSuccessfulUsage(
                        request.run(),
                        request.stage(),
                        currentRequestIndex,
                        request.stage().estimatedInputTokens(),
                        request.promptContents(),
                        result,
                        latencyMs,
                        request.googleSearchEnabled()
                ));
                return result;
            } catch (Exception ex) {
                long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                if (request.countFailedRequestsAfterDispatch()) {
                    executionRepository.saveRunUsage(usageMapper.toFailedUsage(
                            request.run(),
                            request.stage(),
                            currentRequestIndex,
                            request.stage().estimatedInputTokens(),
                            request.promptContents(),
                            latencyMs,
                            ex,
                            request.googleSearchEnabled(),
                            stageExecutionSupport.summarizeException(ex)
                    ));
                }

                if (!isTransientProviderError(ex)
                        || attempt >= retryDelays.size()
                        || !request.retryAllowed().getAsBoolean()) {
                    throw ex;
                }

                long backoffMillis = retryDelays.get(attempt);
                log.warn(
                        "AI provider temporarily unavailable, retrying same request: runId={}, stageId={}, runType={}, requestIndex={}, retryAttempt={}/{}, backoffMs={}, error={}",
                        request.run().id(),
                        request.stage().id(),
                        request.run().runType(),
                        currentRequestIndex,
                        attempt + 1,
                        retryDelays.size(),
                        backoffMillis,
                        stageExecutionSupport.summarizeException(ex)
                );
                sleepBeforeRetry(backoffMillis);
            }
        }
    }

    boolean isTransientProviderError(Exception ex) {
        String summary = stageExecutionSupport.summarizeException(ex).toLowerCase(Locale.ROOT);
        if (ex instanceof RateLimitException
                || QUOTA_HTTP_STATUS.matcher(summary).find()
                || summary.contains("resource_exhausted")
                || summary.contains("quota exhausted")
                || summary.contains("quota exceeded")
                || summary.contains("rate limit")
                || summary.contains("too many requests")) {
            return false;
        }
        return ex instanceof InternalServerException
                || TRANSIENT_HTTP_STATUS.matcher(summary).find()
                || summary.contains("unavailable")
                || summary.contains("temporarily overloaded")
                || summary.contains("currently experiencing high demand")
                || summary.contains("timed out")
                || summary.contains("timeout");
    }

    private void sleepBeforeRetry(long backoffMillis) {
        long remainingMillis = backoffMillis;
        while (remainingMillis > 0) {
            long chunkMillis = Math.min(SLEEP_CHUNK_MILLIS, remainingMillis);
            try {
                Thread.sleep(chunkMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to retry AI provider request", ex);
            }
            remainingMillis -= chunkMillis;
        }
    }
}
