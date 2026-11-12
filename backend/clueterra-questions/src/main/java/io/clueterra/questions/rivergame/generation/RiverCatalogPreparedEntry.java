package io.clueterra.questions.rivergame.generation;

import io.clueterra.questions.rivergame.catalog.RiverCatalogEntry;

public record RiverCatalogPreparedEntry(
        RiverCatalogEntry entry,
        long plainByteSize,
        long bordersByteSize) {
}
