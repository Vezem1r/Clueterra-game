package io.clueterra.questions.countrycatalog.model;

public record CatalogStatistic(String source, String indicator, Double value, String text, Integer year) {
}
