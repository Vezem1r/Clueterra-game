package io.clueterra.questions.clients.mapillary.hintgeneration;

import java.util.Map;

public final class MapillaryPanoramaHintSupport {

    public static final int POOL_SIZE = 50;

    private static final Map<String, Integer> BASE_DIFFICULTIES = Map.of(
            "MAPILLARY_LANDMARK_PANO", 20,
            "MAPILLARY_CITY_PANO", 55,
            "MAPILLARY_RURAL_PANO", 85
    );

    private MapillaryPanoramaHintSupport() {
    }

    public static String extractBase(String indicatorCode) {
        if (indicatorCode != null && indicatorCode.matches(".*_\\d+$")) {
            return indicatorCode.substring(0, indicatorCode.lastIndexOf('_'));
        }
        return indicatorCode;
    }

    public static String templateCode(String indicatorCode, int variant) {
        String base = extractBase(indicatorCode);
        if (!BASE_DIFFICULTIES.containsKey(base)) {
            base = "MAPILLARY_LANDMARK_PANO";
        }
        return base + "_HINT_" + variant;
    }

    public static int baseDifficulty(String indicatorCode) {
        return BASE_DIFFICULTIES.getOrDefault(extractBase(indicatorCode), 55);
    }
}
