package io.clueterra.questions.silhouettegame.model;

import java.util.List;
import java.util.Map;

import io.clueterra.questions.countrycatalog.model.CountryFactSheet;
import io.clueterra.questions.countrycatalog.model.CountryFlagColor;


public record SilhouetteQuestionDraft(
        String iso2,
        int tier,
        String region,
        String subregion,
        List<Double> center,
        List<Double> bounds,
        Map<String, String> names,
        String shapePath,
        List<CountryFlagColor> flagColors,
        CountryFactSheet facts,
        String contentHash,
        List<SilhouetteHintDraft> hints) {
}
