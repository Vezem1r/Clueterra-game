package io.clueterra.questions.rivergame.media;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

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
public class RiverPhotoResolver {

    private static final String COMMONS_FILE_PAGE = "https://commons.wikimedia.org/wiki/File:";
    private static final String OBJECT_PREFIX = "river-photos/";
    private static final String OBJECT_EXTENSION = ".jpg";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String ACTIVE = "ACTIVE";
    private static final String SOURCE = "WIKIMEDIA_COMMONS";
    private static final String NO_IMAGE = "Wikidata lists no image";
    private static final int MIN_WIDTH = 1000;
    private static final double MIN_ASPECT_RATIO = 0.75;
    private static final double MAX_ASPECT_RATIO = 3.2;
    private static final Pattern JPEG_FILE = Pattern.compile("(?i).+\\.jpe?g$");
    private static final Pattern NON_PHOTO_FILE = Pattern.compile(
            "(?iu)(^|[\\s_,.()-])(map|maps|karte|carte|mapa|mappa|карта|locator|location|basin|watershed|"
                    + "catchment|diagram|profile|chart|scheme|schema|drainage)([\\s_,.()-]|$)");

    private final WikimediaImageSourceAuditor wikimediaAuditor;
    private final RiverMediaRepository repository;

    public RiverPhotoResolution resolve(RiverWikidataRecord record, ImageStorageService storage) {
        List<String> failures = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        for (String title : record.commonsFiles()) {
            if (!JPEG_FILE.matcher(title).matches() || NON_PHOTO_FILE.matcher(title).find()) {
                rejections.add(title + ": not a photo file");
                continue;
            }
            ImageSourceValidation validation = wikimediaAuditor.validate(candidate(record, title));
            if (validation.status() == ImageSourceValidationStatus.FETCH_FAILED) {
                failures.add(title + ": " + validation.reason());
                continue;
            }
            Optional<String> rejection = rejection(validation);
            if (rejection.isPresent()) {
                rejections.add(title + ": " + rejection.get());
                continue;
            }
            ImageMetadata metadata = validation.metadata();
            String objectKey = objectKey(metadata.sourceSha1());
            ImageDownloadResult stored = storage.downloadAndStoreDetailed(metadata.downloadUrl(), objectKey);
            if (!stored.success() || !objectKey.equals(stored.objectPath())) {
                failures.add(title + ": " + Optional.ofNullable(stored.failureReason())
                        .orElse("stored as unexpected object " + stored.objectPath()));
                continue;
            }
            return RiverPhotoResolution.found(new RiverPhoto(objectKey, metadata.originalImageUrl(),
                    metadata.sourcePageUrl(), metadata.authorName(), metadata.attributionText(),
                    metadata.licenseLabel(), metadata.licenseUrl(), metadata.sourceSha1(), metadata.mimeType(),
                    metadata.width(), metadata.height(), stored.byteSize(), stored.contentSha256()));
        }
        if (!failures.isEmpty()) {
            return RiverPhotoResolution.failed(String.join("; ", failures));
        }
        return RiverPhotoResolution.none(rejections.isEmpty() ? NO_IMAGE : String.join("; ", rejections));
    }

    private Optional<String> rejection(ImageSourceValidation validation) {
        if (validation.status() != ImageSourceValidationStatus.AVAILABLE) {
            return Optional.of(validation.status().name().toLowerCase(Locale.ROOT));
        }
        ImageMetadata metadata = validation.metadata();
        if (!JPEG_MIME_TYPE.equals(metadata.mimeType()) || metadata.sourceSha1() == null
                || metadata.width() == null || metadata.height() == null) {
            return Optional.of("not a JPEG photo with known dimensions");
        }
        double aspectRatio = (double) metadata.width() / metadata.height();
        if (metadata.width() < MIN_WIDTH || aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            return Optional.of("unsuitable size " + metadata.width() + "x" + metadata.height());
        }
        return repository.findPhotoLifecycleStatus(metadata.originalImageUrl(), metadata.sourcePageUrl())
                .filter(status -> !ACTIVE.equals(status))
                .map(status -> "media lifecycle is " + status.toLowerCase(Locale.ROOT));
    }

    private ImageAuditCandidate candidate(RiverWikidataRecord record, String title) {
        String pageUrl = COMMONS_FILE_PAGE + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
        return new ImageAuditCandidate(ImageAssetOwnerType.RIVER_PHOTO,
                Long.parseLong(record.riverKey().substring(1)), record.riverKey(), null, SOURCE,
                record.riverKey(), null, null, pageUrl, null, null, null, null, null);
    }

    private String objectKey(String sourceSha1) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("river-photo:" + sourceSha1).getBytes(StandardCharsets.UTF_8));
            return OBJECT_PREFIX + HexFormat.of().formatHex(digest) + OBJECT_EXTENSION;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
