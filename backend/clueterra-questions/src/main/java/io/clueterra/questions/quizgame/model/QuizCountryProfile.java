package io.clueterra.questions.quizgame.model;

import java.util.Map;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.CountryCurrency;
import io.clueterra.questions.countrycatalog.model.CountryFactSheet;

public record QuizCountryProfile(
        CountryCatalogEntry entry,
        CountryFactSheet facts,
        int tier,
        String drivingSide,
        CountryCurrency currency,
        boolean coastal,
        Map<QuizMetric, Double> metrics,
        String domain,
        Integer borderCount) {

    public String iso() {
        return entry.iso();
    }

    public Double metric(QuizMetric metric) {
        return metrics.get(metric);
    }
}
