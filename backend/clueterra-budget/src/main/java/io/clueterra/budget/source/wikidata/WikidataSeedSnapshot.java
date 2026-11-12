package io.clueterra.budget.source.wikidata;

import java.util.List;
import java.util.UUID;

public record WikidataSeedSnapshot(UUID snapshotId, List<WikidataDiscoverySeed> seeds) {

    public WikidataSeedSnapshot {
        seeds = List.copyOf(seeds);
    }
}
