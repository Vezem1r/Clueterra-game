package io.clueterra.questions.ai.mapper;

import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeItemType;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiChangeMapperTest {

    @Test
    void allAutoRejectedItemsFinishAsRejectedRatherThanRemainingDraft() {
        Instant now = Instant.parse("2026-10-12T20:00:00Z");
        AiChangeMapper mapper = new AiChangeMapper(Clock.fixed(now, ZoneOffset.UTC));
        AiChangeSet draft = new AiChangeSet(
                1L, 2L, AiChangeSetType.REVIEW, AiChangeSetStatus.DRAFT,
                "Review", "Review", 0, 0, 0, Map.of(), now.minusSeconds(60), null, null);
        AiChangeItem rejected = new AiChangeItem(
                3L, 1L, 2L, null, AiChangeItemType.HINT_UPDATE, AiChangeItemStatus.REJECTED,
                4L, 5L, "Answer leak", "Text edit", Map.of(), Map.of("autoRejected", true), now, now);

        AiChangeSet refreshed = mapper.toRefreshedDraftChangeSet(draft, List.of(rejected));

        assertThat(refreshed.status()).isEqualTo(AiChangeSetStatus.REJECTED);
        assertThat(refreshed.rejectedItems()).isEqualTo(1);
        assertThat(refreshed.rejectedAt()).isEqualTo(now);
    }
}
