package io.clueterra.questions.quizgame.model;

import java.util.List;
import java.util.Map;

public record QuizCountryDraft(
        String iso2,
        int tier,
        int levels,
        String region,
        String subregion,
        Map<String, String> names,
        double longitude,
        double latitude,
        List<String> neighbours,
        String contentHash,
        List<QuizQuestionDraft> questions) {
}
