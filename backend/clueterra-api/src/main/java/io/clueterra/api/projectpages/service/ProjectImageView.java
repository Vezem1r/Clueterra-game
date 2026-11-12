package io.clueterra.api.projectpages.service;

public record ProjectImageView(
        String url,
        String alt,
        String author,
        String attribution,
        String license,
        String licenseUrl,
        String sourcePageUrl) {
}
