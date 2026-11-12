package io.clueterra.questions.clients.mapillary.dto;

import java.util.List;
import java.util.Map;

public record MapillaryCitiesFile(
        Map<String, List<double[]>> cities,
        Map<String, List<double[]>> rural,
        Map<String, List<double[]>> landmarks
) {
}
