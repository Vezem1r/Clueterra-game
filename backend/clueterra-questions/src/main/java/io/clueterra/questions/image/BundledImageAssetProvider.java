package io.clueterra.questions.image;

import io.clueterra.questions.entity.enums.DataSource;

import java.util.Optional;

public interface BundledImageAssetProvider {

    DataSource source();

    ImageMetadata metadata();

    Optional<String> restoreObject(ImageAuditCandidate candidate);

    String unavailableReason();
}
