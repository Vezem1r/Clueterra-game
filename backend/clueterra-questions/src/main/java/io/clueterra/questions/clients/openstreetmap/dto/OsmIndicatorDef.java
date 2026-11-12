package io.clueterra.questions.clients.openstreetmap.dto;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;

public record OsmIndicatorDef(
        String code,
        String rankCode,
        String supCode,
        Category category,
        HintType hintType,
        long minValue,
        int topNForRank,
        boolean generateMainHint) {
}
