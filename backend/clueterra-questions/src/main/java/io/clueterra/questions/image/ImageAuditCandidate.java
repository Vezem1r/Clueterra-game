package io.clueterra.questions.image;

public record ImageAuditCandidate(
        ImageAssetOwnerType ownerType,
        Long ownerId,
        String ownerLabel,
        String countryCode,
        String source,
        String sourceIndicator,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl,
        String recoverySourceUrl,
        Long assetId,
        Long renditionId,
        String provider,
        String sourceKey,
        String storageBucket,
        String objectPath,
        String sourceSha1,
        String lifecycleStatus,
        int consecutiveFailures,
        int ownerCount) {

    public ImageAuditCandidate(
            ImageAssetOwnerType ownerType, Long ownerId, String ownerLabel, String countryCode,
            String source, String sourceIndicator, String imageUrl, String originalImageUrl,
            String imageSourcePageUrl, String imageAuthorName, String imageAttributionText,
            String imageLicenseLabel, String imageLicenseUrl, String recoverySourceUrl) {
        this(ownerType, ownerId, ownerLabel, countryCode, source, sourceIndicator, imageUrl,
                originalImageUrl, imageSourcePageUrl, imageAuthorName, imageAttributionText,
                imageLicenseLabel, imageLicenseUrl, recoverySourceUrl, null, null, null,
                null, "hint-images", null, null, "ACTIVE", 0, 1);
    }

    public ImageAuditCandidate(
            ImageAssetOwnerType ownerType, Long ownerId, String ownerLabel, String countryCode,
            String source, String sourceIndicator, String imageUrl, String originalImageUrl,
            String imageSourcePageUrl, String imageAuthorName, String imageAttributionText,
            String imageLicenseLabel, String imageLicenseUrl, String recoverySourceUrl,
            Long assetId, String provider, String sourceKey, String storageBucket,
            String objectPath, String sourceSha1, String lifecycleStatus,
            int consecutiveFailures, int ownerCount) {
        this(ownerType, ownerId, ownerLabel, countryCode, source, sourceIndicator, imageUrl,
                originalImageUrl, imageSourcePageUrl, imageAuthorName, imageAttributionText,
                imageLicenseLabel, imageLicenseUrl, recoverySourceUrl, assetId, null, provider,
                sourceKey, storageBucket, objectPath, sourceSha1, lifecycleStatus,
                consecutiveFailures, ownerCount);
    }
}
