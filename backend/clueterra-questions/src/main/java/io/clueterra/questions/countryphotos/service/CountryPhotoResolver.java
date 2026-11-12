package io.clueterra.questions.countryphotos.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.clueterra.questions.countryphotos.model.CountryPhoto;
import io.clueterra.questions.countryphotos.model.CountryPhotoCandidate;
import io.clueterra.questions.countryphotos.model.CountryPhotoResolution;
import io.clueterra.questions.countryphotos.model.LandmarkInfo;
import io.clueterra.questions.countryphotos.model.RankedCountryPhoto;
import io.clueterra.questions.countryphotos.repository.CountryPhotoRepository;
import io.clueterra.questions.image.ImageAssetOwnerType;
import io.clueterra.questions.image.ImageAuditCandidate;
import io.clueterra.questions.image.ImageMetadata;
import io.clueterra.questions.image.ImageSourceValidation;
import io.clueterra.questions.image.ImageSourceValidationStatus;
import io.clueterra.questions.image.WikimediaImageSourceAuditor;
import io.clueterra.questions.storage.ImageDownloadResult;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CountryPhotoResolver {

    private static final String OBJECT_PREFIX = "country-photos/";
    private static final String OBJECT_EXTENSION = ".jpg";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String ACTIVE = "ACTIVE";
    private static final String SOURCE = "WIKIMEDIA_COMMONS";
    private static final String NO_CANDIDATES = "No landmark photo passed the size and format checks";

    private final WikimediaImageSourceAuditor wikimediaAuditor;
    private final CountryPhotoRepository repository;

    public CountryPhotoResolution resolve(List<RankedCountryPhoto> ranked, ImageStorageService storage) {
        List<String> failures = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        for (RankedCountryPhoto photo : ranked) {
            ImageSourceValidation validation = wikimediaAuditor.validate(candidate(photo));
            if (validation.status() == ImageSourceValidationStatus.FETCH_FAILED) {
                failures.add(photo.file().title() + ": " + validation.reason());
                continue;
            }
            Optional<String> rejection = rejection(validation);
            if (rejection.isPresent()) {
                rejections.add(photo.file().title() + ": " + rejection.get());
                continue;
            }
            ImageMetadata metadata = validation.metadata();
            String objectKey = objectKey(metadata.sourceSha1());
            ImageDownloadResult stored = storage.downloadAndStoreDetailed(metadata.downloadUrl(), objectKey);
            if (!stored.success() || !objectKey.equals(stored.objectPath())) {
                failures.add(photo.file().title() + ": " + Optional.ofNullable(stored.failureReason())
                        .orElse("stored as unexpected object " + stored.objectPath()));
                continue;
            }
            LandmarkInfo landmark = photo.landmark();
            return CountryPhotoResolution.found(new CountryPhoto(objectKey, metadata.originalImageUrl(),
                    metadata.sourcePageUrl(), metadata.authorName(), metadata.attributionText(),
                    metadata.licenseLabel(), metadata.licenseUrl(), metadata.sourceSha1(), metadata.mimeType(),
                    metadata.width(), metadata.height(), stored.byteSize(), stored.contentSha256(),
                    landmark == null ? null : landmark.qid(), landmark == null ? null : landmark.names(),
                    landmark == null ? null : landmark.sitelinks()));
        }
        if (!failures.isEmpty()) {
            return CountryPhotoResolution.failed(String.join("; ", failures));
        }
        return CountryPhotoResolution.none(rejections.isEmpty() ? NO_CANDIDATES : String.join("; ", rejections));
    }

    private Optional<String> rejection(ImageSourceValidation validation) {
        if (validation.status() != ImageSourceValidationStatus.AVAILABLE) {
            return Optional.of(validation.status().name().toLowerCase(Locale.ROOT));
        }
        ImageMetadata metadata = validation.metadata();
        if (!JPEG_MIME_TYPE.equals(metadata.mimeType()) || metadata.sourceSha1() == null) {
            return Optional.of("not a JPEG photo with a known checksum");
        }
        return repository.findPhotoLifecycleStatus(metadata.originalImageUrl(), metadata.sourcePageUrl())
                .filter(status -> !ACTIVE.equals(status))
                .map(status -> "media lifecycle is " + status.toLowerCase(Locale.ROOT));
    }

    private ImageAuditCandidate candidate(RankedCountryPhoto photo) {
        CountryPhotoCandidate candidate = photo.candidate();
        return new ImageAuditCandidate(ImageAssetOwnerType.COUNTRY_PHOTO, candidate.countryId(), candidate.iso2(),
                candidate.iso2(), SOURCE, candidate.iso2(), null, null, candidate.sourcePageUrl(),
                null, null, null, null, null);
    }

    private String objectKey(String sourceSha1) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("country-photo:" + sourceSha1).getBytes(StandardCharsets.UTF_8));
            return OBJECT_PREFIX + HexFormat.of().formatHex(digest) + OBJECT_EXTENSION;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
