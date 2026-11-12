package io.clueterra.api.budget.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.model.BudgetQuestionType;

class BudgetGameDtoSerializationTest {

    private static final long LARGE_TSID = 9_007_199_254_740_993L;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("should serialize session and variant IDs without JavaScript precision loss")
    void shouldSerializeIdsAsStrings() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(session(LARGE_TSID)));

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("id").asText()).isEqualTo(Long.toString(LARGE_TSID));
        assertThat(json.at("/currentQuestion/variantId").isTextual()).isTrue();
        assertThat(json.at("/currentQuestion/variantId").asText())
                .isEqualTo(Long.toString(LARGE_TSID));
    }

    @Test
    @DisplayName("should deserialize a string variant ID submitted by the frontend")
    void shouldDeserializeStringVariantId() throws Exception {
        BudgetAnswerRequest request = objectMapper.readValue("""
                {
                  "variantId": "9007199254740993",
                  "value": 3200000000
                }
                """, BudgetAnswerRequest.class);

        assertThat(request.variantId()).isEqualTo(LARGE_TSID);
        assertThat(request.value()).isEqualByComparingTo("3200000000");
    }

    @Test
    @DisplayName("should keep project, completion and opening years distinct in a question prompt")
    void shouldSerializeDistinctProjectYears() throws Exception {
        var project = new BudgetPromptProjectDto(
                "Test bridge", "JP", "Japan", "ASIA", BudgetCategory.BRIDGE,
                BudgetProjectLifecycleStatus.COMPLETED, 1990, 1998, 1999, null, "FINAL_COST");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(project));
        BudgetPromptProjectDto restored = objectMapper.treeToValue(json, BudgetPromptProjectDto.class);

        assertThat(json.path("projectYear").asInt()).isEqualTo(1990);
        assertThat(json.path("completionYear").asInt()).isEqualTo(1998);
        assertThat(json.path("openingYear").asInt()).isEqualTo(1999);
        assertThat(restored).isEqualTo(project);
    }

    private static BudgetGameSessionDto session(long id) {
        return new BudgetGameSessionDto(
                id,
                LocalDate.of(2026, 9, 10),
                BudgetGameSessionStatus.IN_PROGRESS,
                true,
                1,
                5,
                0,
                0,
                500,
                20,
                0,
                1,
                Instant.parse("2026-09-10T00:00:00Z"),
                null,
                List.of(),
                question(id));
    }

    private static BudgetChallengeQuestionDto question(long id) {
        var project = new BudgetPromptProjectDto(
                "Akashi Kaikyo Bridge", "JP", "Japan", "ASIA",
                BudgetCategory.BRIDGE, 1998);
        return new BudgetChallengeQuestionDto(
                id, 1, BudgetQuestionType.HOW_MUCH, BudgetDifficulty.MEDIUM,
                new BudgetQuestionPromptDto(project, null, null, null),
                0, List.of(), null, null);
    }
}
