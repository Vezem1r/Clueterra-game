package io.clueterra.questions.countrycatalog.model;

import java.util.List;
import java.util.Map;

public record CountryCatalogEntry(
        String iso,
        Map<String, String> names,
        String region,
        String subregion,
        List<Double> center,
        List<Double> bounds,
        String shapePath,
        List<CountryFlagColor> flagColors,
        List<String> neighbours,
        CatalogReference reference) {
}
