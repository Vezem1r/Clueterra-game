package io.clueterra.questions.ai.mapper;

import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiChangeMapper {

    private final Clock clock;

    public AiChangeMapper(Clock clock) {
        this.clock = clock;
    }

    public AiChangeSet toDraftChangeSet(AiRun run, String title, String summary) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runType", run.runType().name());

        return new AiChangeSet(
                null,
                run.id(),
                run.runType() == AiRunType.REVIEW ? AiChangeSetType.REVIEW : AiChangeSetType.GENERATION,
                AiChangeSetStatus.DRAFT,
                title,
                summary,
                0,
                0,
                0,
                metadata,
                Instant.now(clock),
                null,
                null
        );
    }

    private AiChangeSetStatus resolveStatus(List<AiChangeItem> items) {
        if (items.isEmpty() || items.stream().anyMatch(item -> item.status() == AiChangeItemStatus.PENDING
                || item.status() == AiChangeItemStatus.APPROVED)) {
            return AiChangeSetStatus.DRAFT;
        }
        if (items.stream().allMatch(item -> item.status() == AiChangeItemStatus.APPLIED)) {
            return AiChangeSetStatus.APPLIED;
        }
        if (items.stream().allMatch(item -> item.status() == AiChangeItemStatus.REJECTED)) {
            return AiChangeSetStatus.REJECTED;
        }
        if (items.stream().allMatch(item -> item.status() == AiChangeItemStatus.CANCELLED)) {
            return AiChangeSetStatus.CANCELLED;
        }
        return AiChangeSetStatus.PARTIALLY_APPLIED;
    }

    public AiChangeSet toRefreshedDraftChangeSet(AiChangeSet changeSet, List<AiChangeItem> items) {
        AiChangeSetStatus status = resolveStatus(items);
        return new AiChangeSet(
                changeSet.id(),
                changeSet.runId(),
                changeSet.changeSetType(),
                status,
                changeSet.title(),
                changeSet.summary(),
                items.size(),
                (int) items.stream().filter(item -> item.status() == AiChangeItemStatus.APPLIED).count(),
                (int) items.stream().filter(item -> item.status() == AiChangeItemStatus.REJECTED).count(),
                changeSet.metadata(),
                changeSet.createdAt(),
                changeSet.appliedAt(),
                status == AiChangeSetStatus.REJECTED && changeSet.rejectedAt() == null
                        ? Instant.now(clock) : changeSet.rejectedAt()
        );
    }
}
