package io.clueterra.questions.ai.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiGenerationImageValidationBatchResult;
import io.clueterra.questions.ai.model.review.AiHintReviewBatchResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiStructuredOutputParser {

    private final ObjectMapper objectMapper;

    public AiStructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiHintReviewBatchResult parseReviewBatchResult(Result<?> result) {
        return parse(extractRawText(result), AiHintReviewBatchResult.class);
    }

    public AiHintGenerationBatchResult parseGenerationBatchResult(Result<?> result) {
        return parse(extractRawText(result), AiHintGenerationBatchResult.class);
    }

    public AiGenerationImageValidationBatchResult parseGenerationImageValidationResult(Result<?> result) {
        return parse(extractRawText(result), AiGenerationImageValidationBatchResult.class);
    }

    private <T> T parse(String rawResponse, Class<T> targetType) {
        if (StringUtils.isBlank(rawResponse)) {
            throw new IllegalStateException("AI model returned an empty response");
        }

        String jsonPayload = extractJsonPayload(rawResponse);
        try {
            return objectMapper.readValue(jsonPayload, targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse AI structured output: " + abbreviate(rawResponse), ex);
        }
    }

    private String extractRawText(Result<?> result) {
        if (result == null) {
            throw new IllegalStateException("AI model result is null");
        }

        Object content = result.content();
        if (content instanceof String text && StringUtils.isNotBlank(text)) {
            return text;
        }

        String finalResponseText = extractText(result.finalResponse());
        if (StringUtils.isNotBlank(finalResponseText)) {
            return finalResponseText;
        }

        List<ChatResponse> intermediateResponses = result.intermediateResponses();
        if (intermediateResponses != null) {
            for (int index = intermediateResponses.size() - 1; index >= 0; index--) {
                String intermediateText = extractText(intermediateResponses.get(index));
                if (StringUtils.isNotBlank(intermediateText)) {
                    return intermediateText;
                }
            }
        }

        throw new IllegalStateException(buildEmptyResponseMessage(result));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        return response.aiMessage().text();
    }

    private String buildEmptyResponseMessage(Result<?> result) {
        int toolExecutions = result.toolExecutions() != null ? result.toolExecutions().size() : 0;
        int intermediateResponses = result.intermediateResponses() != null ? result.intermediateResponses().size() : 0;
        String finishReason = result.finishReason() != null ? result.finishReason().name() : "UNKNOWN";
        return "AI model returned an empty response"
                + " (finishReason=" + finishReason
                + ", toolExecutions=" + toolExecutions
                + ", intermediateResponses=" + intermediateResponses
                + ")";
    }

    String extractJsonPayload(String rawResponse) {
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```")) {
            trimmed = stripMarkdownFence(trimmed);
        }

        int startObject = trimmed.indexOf('{');
        int startArray = trimmed.indexOf('[');
        int start = resolveStartIndex(startObject, startArray);
        if (start < 0) {
            return trimmed;
        }

        char opening = trimmed.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int end = findMatchingBracket(trimmed, start, opening, closing);
        if (end < 0) {
            return trimmed.substring(start).trim();
        }

        return trimmed.substring(start, end + 1).trim();
    }

    private String stripMarkdownFence(String value) {
        int firstLineBreak = value.indexOf('\n');
        if (firstLineBreak < 0) {
            return value;
        }

        String withoutOpening = value.substring(firstLineBreak + 1);
        int closingFence = withoutOpening.lastIndexOf("```");
        if (closingFence >= 0) {
            return withoutOpening.substring(0, closingFence).trim();
        }
        return withoutOpening.trim();
    }

    private int resolveStartIndex(int startObject, int startArray) {
        if (startObject < 0) {
            return startArray;
        }
        if (startArray < 0) {
            return startObject;
        }
        return Math.min(startObject, startArray);
    }

    private int findMatchingBracket(String value, int start, char opening, char closing) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);

            if (escaping) {
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == '"') {
                inString = !inString;
            } else if (!inString) {
                if (current == opening) {
                    depth++;
                } else if (current == closing) {
                    depth--;
                    if (depth == 0) {
                        return index;
                    }
                }
            }
        }

        return -1;
    }

    private String abbreviate(String rawResponse) {
        String normalized = rawResponse.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400) + "...";
    }
}
