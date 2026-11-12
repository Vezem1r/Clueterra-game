package io.clueterra.questions.storage;

import java.time.Instant;

public record StoredImageObject(String objectPath, Instant lastModified) {
}
