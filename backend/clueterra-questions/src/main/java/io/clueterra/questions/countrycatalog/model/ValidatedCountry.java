package io.clueterra.questions.countrycatalog.model;

import java.util.List;
import java.util.Map;

public record ValidatedCountry(CountryFactSheet facts, List<Map<String, String>> capitalClue, double rankArea, double rankPopulation) {
}
