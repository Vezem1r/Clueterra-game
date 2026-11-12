package io.clueterra.questions.image;

public record ImageSourceValidation(
        ImageSourceValidationStatus status,
        ImageMetadata metadata,
        String reason) {

    public static ImageSourceValidation available(ImageMetadata metadata) {
        return new ImageSourceValidation(ImageSourceValidationStatus.AVAILABLE, metadata, null);
    }

    public static ImageSourceValidation of(ImageSourceValidationStatus status, String reason) {
        return new ImageSourceValidation(status, null, reason);
    }

    public boolean hasComparableMetadata() {
        return metadata != null
                && (status == ImageSourceValidationStatus.AVAILABLE
                || status == ImageSourceValidationStatus.RESTRICTED_LICENSE);
    }
}
