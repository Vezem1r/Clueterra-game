package io.clueterra.questions.ai.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.TokenUsage;
import io.clueterra.questions.ai.mapper.AiChangeMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiStageExecutionSupport {

    private final AiExecutionRepository executionRepository;
    private final AiChangeMapper changeMapper;
    private final ObjectMapper objectMapper;

    public AiStageExecutionSupport(
            AiExecutionRepository executionRepository,
            AiChangeMapper changeMapper,
            ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.changeMapper = changeMapper;
        this.objectMapper = objectMapper;
    }

    public void refreshDraftChangeSet(AiChangeSet changeSet) {
        List<AiChangeItem> items = executionRepository.findItemsByChangeSetId(changeSet.id());
        executionRepository.saveChangeSet(changeMapper.toRefreshedDraftChangeSet(changeSet, items));
    }

    public List<Long> extractLongList(Map<String, Object> payload, String key) {
        Object value = payload != null ? payload.get(key) : null;
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> {
                    if (item instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.parseLong(String.valueOf(item));
                })
                .toList();
    }

    public long tokenCountIn(TokenUsage tokenUsage) {
        return tokenUsage != null && tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
    }

    public long tokenCountOut(TokenUsage tokenUsage) {
        return tokenUsage != null && tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;
    }

    public String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    public String firstNonBlank(String primary, String fallback) {
        return StringUtils.isNotBlank(primary) ? primary : fallback;
    }

    public String summarizeException(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String rawMessage = firstNonBlank(throwable.getMessage(), root.getMessage());
        if (rawMessage == null) {
            rawMessage = throwable.getClass().getSimpleName();
        }

        if (rawMessage.contains("RESOURCE_EXHAUSTED") || rawMessage.contains("\"code\": 429")) {
            return "Gemini quota exhausted (429 RESOURCE_EXHAUSTED). Check AI Studio rate limits and billing.";
        }
        if (rawMessage.contains("Unsupported file URI type")) {
            return "Gemini rejected a hint image URL because it was not a public HTTP(S)/gs:// file.";
        }
        if (rawMessage.contains("Error while reading: 404")) {
            return "One of the attached review images returned HTTP 404 while LangChain was reading it.";
        }

        String compact = rawMessage.replaceAll("\\s+", " ").trim();
        return compact.length() > 280 ? compact.substring(0, 280) + "..." : compact;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI prompt payload", e);
        }
    }
}
