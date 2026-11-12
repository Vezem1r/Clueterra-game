package io.clueterra.questions.image;

public record ImageRenditionDeletionCandidate(
        Long renditionId,
        String storageBucket,
        String objectKey) {
}
