package io.clueterra.api.admin.silhouettegame.model;

import java.util.Map;

public record SilhouetteLandmark(String qid, Map<String, String> names, int sitelinks) {
}
