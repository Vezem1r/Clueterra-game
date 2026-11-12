package io.clueterra.questions.countryphotos.model;

import java.util.Map;

public record LandmarkInfo(String qid, Map<String, String> names, int sitelinks) {
}
