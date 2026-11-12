package io.clueterra.api.projectpages.repository;

import java.time.Instant;

public record ProjectSitemapRow(String slug, Instant updatedAt) {
}
