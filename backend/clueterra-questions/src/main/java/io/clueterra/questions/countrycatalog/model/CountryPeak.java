package io.clueterra.questions.countrycatalog.model;

import java.util.Map;

public record CountryPeak(Map<String, String> name, double elevation) {
}
