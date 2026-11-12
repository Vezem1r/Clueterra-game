package io.clueterra.questions.countrycatalog.model;

import java.util.Map;

public record CountryCurrency(String code, Map<String, String> name) {
}
