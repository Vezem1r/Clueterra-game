package io.clueterra.api.rivergame.model;

public record RiverRevealRow(
        long id,
        String name,
        String countryCode,
        double lengthKm,
        String bordersImageKey,
        String factsSourceUrl,
        String facts,
        String wikipediaUrl,
        String photoObjectKey,
        String photoAuthorName,
        String photoLicenseLabel,
        String photoLicenseUrl,
        String photoSourcePageUrl) {
}
