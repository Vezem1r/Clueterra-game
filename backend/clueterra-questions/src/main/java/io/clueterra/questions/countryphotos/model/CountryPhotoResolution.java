package io.clueterra.questions.countryphotos.model;

import java.util.Optional;

public record CountryPhotoResolution(CountryPhotoResolutionStatus status, Optional<CountryPhoto> photo, String reason) {

    public static CountryPhotoResolution found(CountryPhoto photo) {
        return new CountryPhotoResolution(CountryPhotoResolutionStatus.FOUND, Optional.of(photo), null);
    }

    public static CountryPhotoResolution none(String reason) {
        return new CountryPhotoResolution(CountryPhotoResolutionStatus.NONE, Optional.empty(), reason);
    }

    public static CountryPhotoResolution failed(String reason) {
        return new CountryPhotoResolution(CountryPhotoResolutionStatus.FAILED, Optional.empty(), reason);
    }
}
