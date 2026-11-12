package io.clueterra.questions.image;

public record MediaGarbageCollectionResult(
        int registeredOrphans,
        int deletedRegistered,
        int deletedUnregistered,
        int failures) {
}
