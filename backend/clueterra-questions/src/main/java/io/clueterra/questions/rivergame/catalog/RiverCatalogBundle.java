package io.clueterra.questions.rivergame.catalog;

import java.util.List;

public record RiverCatalogBundle(String naturalEarthVersion, boolean completeCatalog,
        List<RiverCatalogEntry> entries) {
}
