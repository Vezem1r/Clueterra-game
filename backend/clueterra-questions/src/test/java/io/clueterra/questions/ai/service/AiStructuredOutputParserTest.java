package io.clueterra.questions.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.review.AiHintReviewBatchResult;
import io.clueterra.questions.ai.service.support.AiStructuredOutputParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiStructuredOutputParserTest {

    private final AiStructuredOutputParser parser = new AiStructuredOutputParser(new ObjectMapper());

    @Test
    void shouldParseReviewJsonWrappedInMarkdownFence() {
        String response = """
                ```json
                {
                  "batchSummary": "ok",
                  "items": [
                    {
                      "hintId": 42,
                      "decision": "KEEP",
                      "confidence": 0.98,
                      "summaryReason": "valid",
                      "evidenceSummary": "grounded",
                      "retryAfterDays": 90
                    }
                  ]
                }
                ```
                """;

        AiHintReviewBatchResult result = parser.parseReviewBatchResult(Result.<String>builder().content(response).build());

        assertThat(result.batchSummary()).isEqualTo("ok");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().hintId()).isEqualTo(42L);
        assertThat(result.items().getFirst().decision()).isEqualTo("KEEP");
        assertThat(result.items().getFirst().retryAfterDays()).isEqualTo(90);
    }

    @Test
    void shouldParseGenerationJsonWithLeadingCommentary() {
        String response = """
                Here is the result:
                {
                  "batchSummary": "generated",
                  "proposals": [
                    {
                      "countryId": 7,
                      "category": "GEOGRAPHY",
                      "hintType": "TEXT",
                      "hintText": "Test hint",
                      "difficulty": 55,
                      "qualityScore": 77.0,
                      "factSummary": "fact",
                      "evidenceSummary": "evidence",
                      "duplicateCheckSummary": "clear",
                      "searchTerms": ["alpha", "beta"]
                    }
                  ]
                }
                Thanks!
                """;

        AiHintGenerationBatchResult result = parser.parseGenerationBatchResult(Result.<String>builder().content(response).build());

        assertThat(result.batchSummary()).isEqualTo("generated");
        assertThat(result.proposals()).hasSize(1);
        assertThat(result.proposals().getFirst().countryId()).isEqualTo(7L);
        assertThat(result.proposals().getFirst().hintText()).isEqualTo("Test hint");
    }
}
