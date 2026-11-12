package io.clueterra.questions.clients.openstreetmap.hintgeneration;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;

import java.util.List;

public record OsmComboDef(String code, Category category, HintType hintType, List<String> components) {
}
