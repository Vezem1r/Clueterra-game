package io.clueterra.budget.source.wikidata;

import java.util.UUID;

public record WikidataDiscoveryCheckpoint(
        UUID snapshotId, String rootId, String cursorValue, boolean complete, int recordsBuffered) {
}
