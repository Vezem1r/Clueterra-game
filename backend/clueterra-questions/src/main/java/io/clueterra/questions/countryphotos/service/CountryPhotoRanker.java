package io.clueterra.questions.countryphotos.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.clueterra.questions.countryphotos.model.CommonsFileInfo;
import io.clueterra.questions.countryphotos.model.CountryPhotoCandidate;
import io.clueterra.questions.countryphotos.model.LandmarkInfo;
import io.clueterra.questions.countryphotos.model.RankedCountryPhoto;

@Component
public class CountryPhotoRanker {

    private static final String FILE_PAGE_MARKER = "/wiki/File:";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final int MIN_WIDTH = 800;
    private static final double MIN_ASPECT_RATIO = 0.5;
    private static final double MAX_ASPECT_RATIO = 3.2;
    private static final double LANDSCAPE_ASPECT_RATIO = 1.15;
    private static final double PORTRAIT_WEIGHT = 0.55;
    private static final double UNKNOWN_LANDMARK_SCORE = 0.5;
    private static final double MEGAPIXEL = 1_000_000.0;
    private static final double FULL_DETAIL_MEGAPIXELS = 2;
    private static final double BASE_WEIGHT = 0.8;
    private static final double DETAIL_WEIGHT = 0.2;

    public String title(String sourcePageUrl) {
        int marker = sourcePageUrl.indexOf(FILE_PAGE_MARKER);
        return URLDecoder.decode(sourcePageUrl.substring(marker + FILE_PAGE_MARKER.length())
                .replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' ');
    }

    public List<RankedCountryPhoto> rank(List<CountryPhotoCandidate> candidates, Map<String, CommonsFileInfo> files,
            Map<Long, String> depicted, Map<String, LandmarkInfo> landmarks) {
        return candidates.stream()
                .map(candidate -> Optional.ofNullable(files.get(title(candidate.sourcePageUrl())))
                        .filter(file -> rejection(file).isEmpty())
                        .map(file -> ranked(candidate, file,
                                Optional.ofNullable(depicted.get(file.pageId())).map(landmarks::get).orElse(null)))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RankedCountryPhoto::score).reversed()
                        .thenComparing(photo -> photo.file().width(), Comparator.reverseOrder())
                        .thenComparing(photo -> photo.file().title()))
                .toList();
    }

    private RankedCountryPhoto ranked(CountryPhotoCandidate candidate, CommonsFileInfo file, LandmarkInfo landmark) {
        return new RankedCountryPhoto(candidate, file, landmark, score(file, landmark));
    }

    private Optional<String> rejection(CommonsFileInfo file) {
        double aspectRatio = file.height() == 0 ? 0 : (double) file.width() / file.height();
        if (!JPEG_MIME_TYPE.equals(file.mimeType())) {
            return Optional.of("the file is " + file.mimeType() + ", only JPEG photos are used");
        }
        if (file.width() < MIN_WIDTH) {
            return Optional.of("the photo is " + file.width() + " px wide, at least " + MIN_WIDTH + " px are needed");
        }
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            return Optional.of("the photo is " + file.width() + " x " + file.height() + " px, too narrow or too wide for the card");
        }
        return Optional.empty();
    }

    private double score(CommonsFileInfo file, LandmarkInfo landmark) {
        double popularity = landmark == null ? UNKNOWN_LANDMARK_SCORE : landmark.sitelinks();
        double shape = (double) file.width() / file.height() >= LANDSCAPE_ASPECT_RATIO ? 1 : PORTRAIT_WEIGHT;
        double detail = Math.min(1, file.width() * (double) file.height() / (FULL_DETAIL_MEGAPIXELS * MEGAPIXEL));
        return popularity * shape * (BASE_WEIGHT + DETAIL_WEIGHT * detail);
    }
}
