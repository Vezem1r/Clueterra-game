package io.clueterra.questions.storage;

public record ImageDownloadResult(
        boolean success,
        String objectPath,
        Integer httpStatus,
        String failureReason,
        Long byteSize,
        String contentSha256,
        String contentType) {

    public static ImageDownloadResult stored(String objectPath, int httpStatus) {
        return new ImageDownloadResult(true, objectPath, httpStatus, null, null, null, null);
    }

    public static ImageDownloadResult stored(
            String objectPath,
            int httpStatus,
            long byteSize,
            String contentSha256,
            String contentType) {
        return new ImageDownloadResult(
                true, objectPath, httpStatus, null, byteSize, contentSha256, contentType);
    }

    public static ImageDownloadResult failed(Integer httpStatus, String failureReason) {
        return new ImageDownloadResult(false, null, httpStatus, failureReason, null, null, null);
    }
}
