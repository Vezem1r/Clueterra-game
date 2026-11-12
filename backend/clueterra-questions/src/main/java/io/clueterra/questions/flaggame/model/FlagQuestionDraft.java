package io.clueterra.questions.flaggame.model;

import java.util.List;
import java.util.Map;

public record FlagQuestionDraft(
        String iso2,
        int tier,
        String region,
        String subregion,
        Map<String, String> names,
        String contentHash,
        List<FlagHintDraft> hints) {
}
