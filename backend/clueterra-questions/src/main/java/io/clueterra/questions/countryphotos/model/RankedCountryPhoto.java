package io.clueterra.questions.countryphotos.model;

public record RankedCountryPhoto(CountryPhotoCandidate candidate, CommonsFileInfo file, LandmarkInfo landmark, double score) {
}
