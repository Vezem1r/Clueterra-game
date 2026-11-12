package io.clueterra.questions.clients.worldbank;

import io.clueterra.questions.entity.enums.Category;

import java.math.BigDecimal;

public record IndicatorConfig(String name, Category category, String unit, BigDecimal weight) {
}
