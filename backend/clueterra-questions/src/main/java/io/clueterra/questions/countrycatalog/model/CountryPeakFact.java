package io.clueterra.questions.countrycatalog.model;

import java.util.Map;

public record CountryPeakFact(Map<String, String> name, long elevationM) {
}
