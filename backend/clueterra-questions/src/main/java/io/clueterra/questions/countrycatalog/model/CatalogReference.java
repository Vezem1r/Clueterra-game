package io.clueterra.questions.countrycatalog.model;

import java.util.List;
import java.util.Map;

public record CatalogReference(
        List<Map<String, String>> capitals,
        List<Double> populations,
        List<Double> areas,
        List<CountryPeak> peaks,
        List<CountryCurrency> currencies,
        boolean landlocked,
        double geometryAreaKm2) {
}
