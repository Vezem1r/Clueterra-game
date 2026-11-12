package io.clueterra.questions.rivergame.catalog;

import java.util.List;

public record RiverCatalogCourse(
        List<Double> bbox,
        List<List<List<Double>>> coordinates) {
}
