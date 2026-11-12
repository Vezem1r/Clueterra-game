package io.clueterra.budget.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.springframework.stereotype.Service;

import io.clueterra.budget.config.WikimediaProperties;
import io.clueterra.budget.image.DownloadedBudgetImage;
import io.clueterra.budget.image.WikimediaImageCandidate;
import io.clueterra.budget.project.BudgetCandidateImageWorkItem;
import io.clueterra.budget.project.BudgetCandidateStoredImage;
import io.clueterra.budget.source.wikimedia.WikimediaCommonsClient;
import io.clueterra.budget.storage.BudgetImageStorageService;
import io.clueterra.budget.storage.BudgetStorageProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WikidataCandidateImageService {

    private final WikimediaProperties wikimediaProperties;
    private final BudgetStorageProperties storageProperties;
    private final WikimediaCommonsClient commonsClient;
    private final BudgetImageStorageService storageService;

    public BudgetCandidateStoredImage downloadAndStore(BudgetCandidateImageWorkItem workItem) {
        if (!wikimediaProperties.enabled() || !storageProperties.enabled()) {
            throw new IllegalStateException("Budget candidate image storage is disabled");
        }
        try {
            WikimediaImageCandidate candidate =
                    commonsClient
                            .findExactImage(
                                    workItem.imageFileTitle(),
                                    workItem.authorNameOverride(),
                                    workItem.creditOverride())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No reusable Wikimedia image for " + workItem.imageFileTitle()));
            Optional<BudgetCandidateStoredImage> existing = existingStoredImage(candidate);
            if (existing.isPresent()) {
                return existing.orElseThrow();
            }
            DownloadedBudgetImage downloaded = commonsClient.download(candidate);
            String objectKey = candidateObjectKey(candidate, downloaded.extension());
            storageService.store(objectKey, downloaded);
            return storedImage(candidate, objectKey, downloaded.contentType());
        } finally {
            pauseBetweenRequests();
        }
    }

    private Optional<BudgetCandidateStoredImage> existingStoredImage(
            WikimediaImageCandidate candidate) {
        LinkedHashSet<String> extensions =
                new LinkedHashSet<>(List.of(extension(candidate.mimeType()), "jpg", "png", "webp"));
        return extensions.stream()
                .map(extension -> candidateObjectKey(candidate, extension))
                .map(
                        objectKey ->
                                storageService
                                        .contentType(objectKey)
                                        .map(contentType -> storedImage(candidate, objectKey, contentType)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private BudgetCandidateStoredImage storedImage(
            WikimediaImageCandidate candidate, String objectKey, String contentType) {
        return new BudgetCandidateStoredImage(
                candidate.pageId(),
                candidate.sourceSha1(),
                candidate.title(),
                objectKey,
                candidate.originalUrl(),
                candidate.sourcePageUrl(),
                candidate.authorName(),
                candidate.credit(),
                candidate.licenseLabel(),
                candidate.licenseUrl(),
                candidate.description(),
                contentType,
                candidate.sourceBytes(),
                candidate.width(),
                candidate.height(),
                imageQualityScore(candidate));
    }

    private void pauseBetweenRequests() {
        if (!wikimediaProperties.requestDelay().isZero() && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(wikimediaProperties.requestDelay().toNanos());
        }
    }

    private String candidateObjectKey(WikimediaImageCandidate image, String extension) {
        return "candidates/wikimedia/"
                + image.pageId()
                + "/"
                + safeSha1(image.sourceSha1())
                + "."
                + extension;
    }

    private String safeSha1(String sourceSha1) {
        String safe = sourceSha1 == null ? "" : sourceSha1.replaceAll("[^a-zA-Z0-9]", "");
        return safe.isBlank() ? "current" : safe;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private int imageQualityScore(WikimediaImageCandidate candidate) {
        double megapixels = (candidate.width() * (double) candidate.height()) / 1_000_000.0;
        int resolutionScore = Math.min(55, (int) Math.round(Math.log1p(megapixels) * 22));
        double aspectRatio = candidate.width() / (double) candidate.height();
        int compositionScore;
        if (aspectRatio >= 1.2 && aspectRatio <= 2.4) {
            compositionScore = 30;
        } else if (aspectRatio >= 0.7 && aspectRatio <= 3.2) {
            compositionScore = 20;
        } else {
            compositionScore = 8;
        }
        return Math.min(100, resolutionScore + compositionScore + 15);
    }
}
