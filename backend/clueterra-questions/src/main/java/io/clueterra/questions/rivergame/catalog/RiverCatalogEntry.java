package io.clueterra.questions.rivergame.catalog;

import java.util.List;

public record RiverCatalogEntry(
        String countryCode,
        String riverKey,
        String nameEn,
        List<String> aliases,
        int rankInCountry,
        double lengthKm,
        String plainImageKey,
        String bordersImageKey,
        RiverCatalogCourse course,
        String geometrySourceUrl,
        String geometryAttribution,
        String factsSourceUrl,
        List<RiverCatalogHint> hints,
        List<RiverCatalogFact> facts,
        String contentHash) {
}
