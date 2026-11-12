package io.clueterra.questions.countrycatalog.model;

import java.util.List;

public record CountryCatalogBundle(int version, String borders, List<CountryCatalogEntry> entries) {
}
