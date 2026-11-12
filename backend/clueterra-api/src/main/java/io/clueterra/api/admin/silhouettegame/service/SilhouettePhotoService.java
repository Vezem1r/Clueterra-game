package io.clueterra.api.admin.silhouettegame.service;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.admin.hints.image.DownloadedWikimediaImage;
import io.clueterra.api.admin.hints.image.WikimediaCommonsImageClient;
import io.clueterra.api.admin.hints.image.WikimediaHintImage;
import io.clueterra.api.admin.hints.image.WikimediaImageException;
import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.clueterra.api.admin.silhouettegame.client.CommonsLandmarkClient;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoChangeResponse;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoFollowUp;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoReplaceRequest;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewDto;
import io.clueterra.api.admin.silhouettegame.model.SilhouetteLandmark;
import io.clueterra.api.admin.silhouettegame.model.SilhouettePhotoUpdate;
import io.clueterra.api.admin.silhouettegame.repository.SilhouetteGeneratedContentJdbcRepository;
import io.clueterra.api.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SilhouettePhotoService {

    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String OBJECT_PREFIX = "country-photos/";
    private static final String OBJECT_EXTENSION = ".jpg";
    private static final String OBJECT_KEY_SEED = "country-photo:";
    private static final String FILE_PAGE_MARKER = "/wiki/";
    private static final String FILE_PREFIX = "File:";
    private static final String ACTIVE = "ACTIVE";
    private static final double MIN_ASPECT_RATIO = 0.5;
    private static final double MAX_ASPECT_RATIO = 3.2;
    private static final String GENERATE_CLUES_PATH = "/api/admin/silhouettes/generate";
    private static final String SYNC_PHOTOS_PATH = "/api/admin/countries/photos/sync";

    private final SilhouetteGeneratedContentJdbcRepository repository;
    private final WikimediaCommonsImageClient wikimediaImageClient;
    private final CommonsLandmarkClient landmarkClient;
    private final StorageService storageService;
    private final ClueterraQuestionsClient questionsClient;

    public SilhouettePhotoChangeResponse replace(String iso2, SilhouettePhotoReplaceRequest request, String selectedBy) {
        SilhouetteQuestionPreviewDto question = question(iso2);
        WikimediaHintImage image = wikimediaImageClient.findExactImage(title(request.fileTitle()));
        double aspectRatio = (double) image.width() / image.height();
        if (!JPEG_MIME_TYPE.equals(image.mimeType())) {
            throw invalid("Only JPEG photos are used for countries; this file is " + image.mimeType());
        }
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            throw invalid("The photo is " + image.width() + " x " + image.height() + " px, too narrow or too wide for the card");
        }
        repository.findPhotoLifecycleStatus(image.originalUrl(), image.sourcePageUrl())
                .filter(status -> !ACTIVE.equals(status))
                .ifPresent(status -> {
                    throw invalid("This image is " + status.toLowerCase(Locale.ROOT) + " in the media library");
                });
        Optional<SilhouetteLandmark> landmark = landmark(image, request.landmarkQid());

        DownloadedWikimediaImage downloaded = wikimediaImageClient.downloadThumbnail(image);
        if (!JPEG_MIME_TYPE.equals(downloaded.contentType())) {
            throw invalid("Wikimedia Commons served " + downloaded.contentType() + " instead of a JPEG photo");
        }
        String objectKey = OBJECT_PREFIX + sha256(OBJECT_KEY_SEED + image.sourceSha1()) + OBJECT_EXTENSION;
        try {
            storageService.uploadToHintBucket(objectKey, new ByteArrayInputStream(downloaded.bytes()),
                    downloaded.bytes().length, downloaded.contentType());
        } catch (RuntimeException exception) {
            log.error("Failed to store the country photo for {}: {}", question.countryCode(), exception.getMessage(), exception);
            throw new WikimediaImageException(HttpStatus.BAD_GATEWAY,
                    "The Wikimedia image was valid but could not be stored", exception);
        }

        log.info("Country photo for {} replaced with {} by {}", question.countryCode(), image.title(), selectedBy);
        repository.saveManual(question.countryCode(), Optional.of(new SilhouettePhotoUpdate(objectKey, image.originalUrl(),
                image.sourcePageUrl(), image.authorName(), image.attributionText(), image.licenseLabel(),
                image.licenseUrl(), image.sourceSha1(), image.mimeType(), image.width(), image.height(),
                downloaded.bytes().length, sha256(downloaded.bytes()),
                landmark.map(SilhouetteLandmark::qid).orElse(null), landmark.map(SilhouetteLandmark::names).orElse(null),
                landmark.map(SilhouetteLandmark::sitelinks).orElse(null))), selectedBy);
        return changed(question);
    }

    public SilhouettePhotoChangeResponse remove(String iso2, String selectedBy) {
        SilhouetteQuestionPreviewDto question = question(iso2);
        log.info("Country photo for {} removed by {}", question.countryCode(), selectedBy);
        repository.saveManual(question.countryCode(), Optional.empty(), selectedBy);
        return changed(question);
    }

    public SilhouettePhotoChangeResponse automatic(String iso2) {
        SilhouetteQuestionPreviewDto question = question(iso2);
        if (!repository.release(question.countryCode())) {
            return new SilhouettePhotoChangeResponse(question, SilhouettePhotoFollowUp.NONE);
        }
        log.info("Country photo for {} returned to the automatic choice", question.countryCode());
        questionsClient.fireAndForget("Country photo sync", SYNC_PHOTOS_PATH);
        return new SilhouettePhotoChangeResponse(question(iso2), SilhouettePhotoFollowUp.SYNC_STARTED);
    }

    private SilhouettePhotoChangeResponse changed(SilhouetteQuestionPreviewDto before) {
        SilhouetteQuestionPreviewDto after = question(before.countryCode());
        if ((before.photoUrl() == null) == (after.photoUrl() == null)) {
            return new SilhouettePhotoChangeResponse(after, SilhouettePhotoFollowUp.NONE);
        }
        questionsClient.fireAndForget("Silhouette clues", GENERATE_CLUES_PATH);
        return new SilhouettePhotoChangeResponse(after, SilhouettePhotoFollowUp.CLUES_REGENERATING);
    }

    private SilhouetteQuestionPreviewDto question(String iso2) {
        return repository.findQuestion(iso2)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Country is not in the silhouette pool"));
    }

    private Optional<SilhouetteLandmark> landmark(WikimediaHintImage image, String landmarkQid) {
        if (landmarkQid != null && !landmarkQid.isBlank()) {
            return Optional.of(landmarkClient.landmark(landmarkQid)
                    .orElseThrow(() -> invalid("Wikidata has no item " + landmarkQid + " with an English name")));
        }
        return landmarkClient.depicted(image.title().substring(FILE_PREFIX.length()))
                .flatMap(landmarkClient::landmark);
    }

    private String title(String input) {
        String trimmed = input.trim();
        int marker = trimmed.indexOf(FILE_PAGE_MARKER);
        String title = URLDecoder.decode((marker < 0 ? trimmed : trimmed.substring(marker + FILE_PAGE_MARKER.length()))
                .replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' ');
        return title.regionMatches(true, 0, FILE_PREFIX, 0, FILE_PREFIX.length())
                ? title.substring(FILE_PREFIX.length()).trim()
                : title;
    }

    private static WikimediaImageException invalid(String message) {
        return new WikimediaImageException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
