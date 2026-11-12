package io.clueterra.questions.ai.model.dispatch;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public record AiProviderDispatchRequest(
        AiRun run,
        AiRunStage stage,
        AtomicInteger requestIndex,
        List<Content> promptContents,
        boolean countFailedRequestsAfterDispatch,
        boolean googleSearchEnabled,
        Runnable beforeAttempt,
        BooleanSupplier retryAllowed,
        Supplier<Result<String>> providerCall) {
}
