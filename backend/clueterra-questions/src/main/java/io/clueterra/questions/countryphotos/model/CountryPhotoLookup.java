package io.clueterra.questions.countryphotos.model;

import java.util.Map;

public record CountryPhotoLookup(Map<String, CommonsFileInfo> files, Map<Long, String> depicted,
        Map<String, LandmarkInfo> landmarks) {
}
