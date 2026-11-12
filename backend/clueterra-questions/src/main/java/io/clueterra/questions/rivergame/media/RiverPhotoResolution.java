package io.clueterra.questions.rivergame.media;

import java.util.Optional;

public record RiverPhotoResolution(RiverPhotoResolutionStatus status, Optional<RiverPhoto> photo, String reason) {

    public static RiverPhotoResolution found(RiverPhoto photo) {
        return new RiverPhotoResolution(RiverPhotoResolutionStatus.FOUND, Optional.of(photo), null);
    }

    public static RiverPhotoResolution none(String reason) {
        return new RiverPhotoResolution(RiverPhotoResolutionStatus.NONE, Optional.empty(), reason);
    }

    public static RiverPhotoResolution failed(String reason) {
        return new RiverPhotoResolution(RiverPhotoResolutionStatus.FAILED, Optional.empty(), reason);
    }
}
