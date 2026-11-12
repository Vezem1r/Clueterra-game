package io.clueterra.questions.image;

import io.clueterra.questions.clients.common.ImageRightsSupport;
import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.entity.OperationTriggerType;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.challenge.ChallengeRerollService;
import io.clueterra.questions.storage.ImageDownloadResult;
import io.clueterra.questions.storage.ImageStorageService;
import io.clueterra.questions.storage.MediaStorageAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.function.Predicate;

@Service
@Slf4j
public class ImageIntegrityService {

    private static final String MAINTENANCE_LEASE = "image-integrity-audit";
    private static final Duration MAINTENANCE_LEASE_DURATION = Duration.ofHours(2);

    private final ImageAssetRepository imageAssetRepository;
    private final Optional<ImageStorageService> imageStorageService;
    private final Map<String, BundledImageAssetProvider> bundledAssetProviders;
    private final WikimediaImageSourceAuditor wikimediaAuditor;
    private final Optional<MapillaryImageSourceAuditor> mapillaryAuditor;
    private final ChallengeRerollService challengeRerollService;
    private final OperationBatchLogRepository batchLogRepository;
    private final ConsoleLogWriter console;
    private final AsyncTaskExecutor executor;

    public ImageIntegrityService(
            ImageAssetRepository imageAssetRepository,
            Optional<ImageStorageService> imageStorageService,
            List<BundledImageAssetProvider> bundledAssetProviders,
            WikimediaImageSourceAuditor wikimediaAuditor,
            Optional<MapillaryImageSourceAuditor> mapillaryAuditor,
            ChallengeRerollService challengeRerollService,
            OperationBatchLogRepository batchLogRepository,
            ConsoleLogWriter console,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.imageAssetRepository = imageAssetRepository;
        this.imageStorageService = imageStorageService;
        this.bundledAssetProviders = bundledAssetProviders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> provider.source().name(),
                        Function.identity()));
        this.wikimediaAuditor = wikimediaAuditor;
        this.mapillaryAuditor = mapillaryAuditor;
        this.challengeRerollService = challengeRerollService;
        this.batchLogRepository = batchLogRepository;
        this.console = console;
        this.executor = executor;
    }

    public String startManualAudit() {
        requireStorage();
        String operationId = "image_integrity-" + UUID.randomUUID().toString().substring(0, 8);
        console.startOperation(operationId, "Image integrity audit and repair");
        OperationBatchLog batchLog;
        try {
            batchLog = startBatch(OperationTriggerType.MANUAL, operationId);
        } catch (RuntimeException e) {
            console.failOperation(operationId, e.getMessage());
            throw e;
        }
        console.system("Starting image integrity audit and repair...");
        executor.execute(() -> runAudit(operationId, batchLog, true));
        return operationId;
    }

    public void runScheduledAudit() {
        if (imageStorageService.isEmpty()) {
            log.info("[IMAGE_INTEGRITY] Storage disabled, scheduled audit skipped");
            return;
        }
        String operationId = "image_integrity-scheduled-" + UUID.randomUUID().toString().substring(0, 8);
        OperationBatchLog batchLog = startBatch(OperationTriggerType.SCHEDULED, operationId);
        runAudit(operationId, batchLog, false);
    }

    ImageIntegrityReport audit() {
        return audit(false);
    }

    private ImageIntegrityReport audit(boolean updateConsole) {
        ImageStorageService storage = requireStorage();
        List<ImageAuditCandidate> queuedCandidates = imageAssetRepository.findAuditCandidates();
        queuedCandidates.stream()
                .map(ImageAuditCandidate::storageBucket)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(storage::verifyBucketAccess);
        Map<String, Boolean> storagePresence = new HashMap<>();
        Map<String, ImageSourceValidation> sourceValidations = new HashMap<>();
        Map<Long, Integer> validationFailures = new HashMap<>();
        Set<Long> quarantinedResources = new HashSet<>();
        List<ImageAuditOutcome> outcomes = new ArrayList<>(queuedCandidates.size());

        for (int index = 0; index < queuedCandidates.size(); index++) {
            ImageAuditCandidate candidate = queuedCandidates.get(index);
            ImageAuditOutcome outcome = auditCandidate(candidate, storage, storagePresence, sourceValidations);
            outcomes.add(outcome);
            applyLifecycle(outcome, validationFailures, quarantinedResources);
            int processed = index + 1;
            if (updateConsole && (processed % 25 == 0 || processed == queuedCandidates.size())) {
                console.info("Image integrity: scanned " + processed + "/" + queuedCandidates.size());
            }
        }

        return buildReport(outcomes, sourceValidations.size());
    }

    private void runAudit(String operationId, OperationBatchLog batchLog, boolean updateConsole) {
        String leaseOwner = operationId + "-" + UUID.randomUUID();
        if (!imageAssetRepository.tryAcquireMaintenanceLease(
                MAINTENANCE_LEASE,
                leaseOwner,
                MAINTENANCE_LEASE_DURATION)) {
            IllegalStateException error = new IllegalStateException("Another image integrity audit is already running");
            batchLog.fail(error);
            batchLog.setSummary(Map.of("operationId", operationId, "mode", "IMAGE_INTEGRITY_AUDIT"));
            batchLogRepository.update(batchLog);
            if (updateConsole) {
                console.error(error.getMessage());
                console.failOperation(operationId, error.getMessage());
            }
            return;
        }

        try {
            ImageIntegrityReport report = audit(updateConsole);
            batchLog.setTotalItemsCount(report.totalItems());
            batchLog.setSuccessfulItems(report.successfulItems());
            batchLog.setFailedItems(report.failedItems());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("operationId", operationId);
            summary.putAll(report.summary());
            batchLog.setSummary(summary);
            batchLog.complete();
            batchLogRepository.update(batchLog);

            String completion = String.format(
                    "Image integrity done: %d scanned, %d restored, %d download failures, %d source/license/metadata issues",
                    report.totalItems(),
                    number(report.summary(), "downloaded"),
                    number(report.summary(), "downloadFailed"),
                    report.failedItems());
            log.info("[op={}] {}", operationId, completion);
            if (updateConsole) {
                console.success(completion);
                console.completeOperation(operationId);
            }
        } catch (Exception e) {
            log.error("[op={}] Image integrity audit failed: {}", operationId, e.getMessage(), e);
            batchLog.fail(e);
            batchLog.setSummary(Map.of("operationId", operationId, "mode", "IMAGE_INTEGRITY_AUDIT"));
            batchLogRepository.update(batchLog);
            if (updateConsole) {
                console.error("Image integrity audit failed: " + e.getMessage());
                console.failOperation(operationId, e.getMessage());
            } else {
                throw (RuntimeException) e;
            }
        } finally {
            imageAssetRepository.releaseMaintenanceLease(MAINTENANCE_LEASE, leaseOwner);
        }
    }

    private ImageAuditOutcome auditCandidate(
            ImageAuditCandidate candidate,
            ImageStorageService storage,
            Map<String, Boolean> storagePresence,
            Map<String, ImageSourceValidation> sourceValidations) {
        try {
            String objectPath = StringUtils.hasText(candidate.objectPath())
                    ? candidate.objectPath()
                    : extractObjectPath(storage, candidate.storageBucket(), candidate.imageUrl());
            String storageKey = candidate.storageBucket() + "/" + objectPath;
            String lookupPath = objectPath;
            boolean storageMissing = objectPath == null
                    || !storagePresence.computeIfAbsent(
                            storageKey,
                            ignored -> objectExists(storage, candidate.storageBucket(), lookupPath));
            imageAssetRepository.recordStorageCheck(candidate.renditionId(), !storageMissing);
            ImageSourceValidation validation = validateSource(candidate, sourceValidations);
            List<String> metadataChanges = validation.hasComparableMetadata()
                    ? compareMetadata(candidate, validation.metadata())
                    : List.of();
            boolean metadataBackfilled = false;
            boolean restored = false;
            String repairFailure = null;
            boolean sourceContentChanged = metadataChanges.contains("SOURCE_CONTENT");

            if (storageMissing && sourceContentChanged) {
                repairFailure = "Stored image is missing and the external source content changed; automatic replacement was blocked";
            } else if (storageMissing) {
                ImageDownloadResult repair = restore(candidate, validation, objectPath, storage);
                if (repair.success()) {
                    restored = true;
                    objectPath = repair.objectPath();
                    storagePresence.put(candidate.storageBucket() + "/" + objectPath, true);
                    imageAssetRepository.updateAfterRepair(candidate, objectPath, safeMetadata(validation));
                    imageAssetRepository.recordRenditionContent(candidate.storageBucket(), repair);
                    metadataBackfilled = hasBackfillableMetadata(candidate, validation.metadata());
                } else {
                    repairFailure = repair.failureReason();
                }
            } else if (validation.hasComparableMetadata()
                    && (hasBackfillableMetadata(candidate, validation.metadata())
                    || !metadataChanges.isEmpty())) {
                imageAssetRepository.updateAfterRepair(
                        candidate,
                        objectPath,
                        metadataForAutomaticUpdate(candidate, validation.metadata()));
                metadataBackfilled = hasBackfillableMetadata(candidate, validation.metadata());
            }

            return new ImageAuditOutcome(
                    candidate,
                    storageMissing,
                    restored,
                    repairFailure,
                    validation,
                    metadataChanges,
                    metadataBackfilled,
                    objectPath,
                    metadataMap(validation.metadata()));
        } catch (MediaStorageAccessException exception) {
            throw exception;
        } catch (Exception e) {
            return new ImageAuditOutcome(
                    candidate,
                    false,
                    false,
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    ImageSourceValidation.of(ImageSourceValidationStatus.FETCH_FAILED, e.getMessage()),
                    List.of(),
                    false,
                    null,
                    Map.of());
        }
    }

    private ImageSourceValidation validateSource(
            ImageAuditCandidate candidate,
            Map<String, ImageSourceValidation> sourceValidations) {
        BundledImageAssetProvider bundledProvider = bundledAssetProviders.get(candidate.source());
        if (bundledProvider != null) {
            return ImageSourceValidation.available(bundledProvider.metadata());
        }
        if (mapillaryAuditor.isPresent() && mapillaryAuditor.orElseThrow().supports(candidate)) {
            String cacheKey = firstNonBlank(candidate.sourceKey(), candidate.imageSourcePageUrl());
            return sourceValidations.computeIfAbsent(
                    cacheKey,
                    ignored -> mapillaryAuditor.orElseThrow().validate(candidate));
        }
        if (!wikimediaAuditor.supports(candidate)) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.UNSUPPORTED,
                    "This source does not expose machine-readable per-image attribution metadata");
        }
        String cacheKey = firstNonBlank(
                candidate.imageSourcePageUrl(),
                candidate.originalImageUrl(),
                candidate.recoverySourceUrl());
        return sourceValidations.computeIfAbsent(cacheKey, ignored -> wikimediaAuditor.validate(candidate));
    }

    private ImageDownloadResult restore(
            ImageAuditCandidate candidate,
            ImageSourceValidation validation,
            String existingObjectPath,
            ImageStorageService storage) {
        BundledImageAssetProvider bundledProvider = bundledAssetProviders.get(candidate.source());
        if (bundledProvider != null) {
            return bundledProvider.restoreObject(candidate)
                    .map(path -> ImageDownloadResult.stored(path, 200))
                    .orElseGet(() -> ImageDownloadResult.failed(null, bundledProvider.unavailableReason()));
        }
        if (validation.status() == ImageSourceValidationStatus.SOURCE_MISSING
                || validation.status() == ImageSourceValidationStatus.RESTRICTED_LICENSE
                || validation.status() == ImageSourceValidationStatus.FETCH_FAILED) {
            return ImageDownloadResult.failed(null, validation.reason());
        }

        String sourceUrl = validation.metadata() != null
                ? firstNonBlank(
                        validation.metadata().downloadUrl(),
                        validation.metadata().originalImageUrl())
                : firstNonBlank(candidate.recoverySourceUrl(), candidate.imageUrl());
        if (!StringUtils.hasText(sourceUrl)) {
            return ImageDownloadResult.failed(null, "No recoverable original image URL is recorded");
        }
        String targetPath = StringUtils.hasText(existingObjectPath)
                ? existingObjectPath
                : deterministicObjectPath(candidate);
        return "hint-images".equals(candidate.storageBucket())
                ? storage.downloadAndStoreDetailed(sourceUrl, targetPath)
                : storage.downloadAndStoreDetailed(candidate.storageBucket(), sourceUrl, targetPath);
    }

    private boolean objectExists(ImageStorageService storage, String bucket, String objectPath) {
        return storage.objectExistsStrict(bucket, objectPath);
    }

    private String extractObjectPath(ImageStorageService storage, String bucket, String imageUrl) {
        return "hint-images".equals(bucket)
                ? storage.extractObjectPath(imageUrl)
                : storage.extractObjectPath(bucket, imageUrl);
    }

    private ImageIntegrityReport buildReport(List<ImageAuditOutcome> outcomes, int uniqueSourcesChecked) {
        int failed = (int) outcomes.stream().filter(ImageAuditOutcome::hasActionableIssue).count();
        List<Map<String, Object>> anomalies = outcomes.stream()
                .filter(ImageAuditOutcome::hasActionableIssue)
                .map(this::findingMap)
                .toList();
        List<Map<String, Object>> findings = outcomes.stream()
                .filter(ImageAuditOutcome::hasReportableResult)
                .map(this::findingMap)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "IMAGE_INTEGRITY_AUDIT");
        summary.put("scanned", outcomes.size());
        summary.put("storagePresent", count(outcomes, outcome -> !outcome.storageMissing()));
        summary.put("storageMissing", count(outcomes, ImageAuditOutcome::storageMissing));
        summary.put("downloaded", count(outcomes, ImageAuditOutcome::restored));
        summary.put("downloadFailed", count(outcomes, outcome -> outcome.repairFailure() != null));
        summary.put("uniqueExternalSourcesChecked", uniqueSourcesChecked);
        summary.put("validatedOk", count(outcomes,
                outcome -> outcome.validation().status() == ImageSourceValidationStatus.AVAILABLE));
        summary.put("validationUnsupported", count(outcomes,
                outcome -> outcome.validation().status() == ImageSourceValidationStatus.UNSUPPORTED));
        summary.put("sourceMissing", count(outcomes,
                outcome -> outcome.validation().status() == ImageSourceValidationStatus.SOURCE_MISSING));
        summary.put("restrictedLicense", count(outcomes,
                outcome -> outcome.validation().status() == ImageSourceValidationStatus.RESTRICTED_LICENSE));
        summary.put("validationFailed", count(outcomes,
                outcome -> outcome.validation().status() == ImageSourceValidationStatus.FETCH_FAILED));
        summary.put("licenseChanged", count(outcomes, outcome -> outcome.metadataChanges().contains("LICENSE")));
        summary.put("attributionChanged", count(outcomes, outcome -> outcome.metadataChanges().contains("ATTRIBUTION")));
        summary.put("sourceUrlChanged", count(outcomes, outcome -> outcome.metadataChanges().contains("SOURCE_URL")));
        summary.put("metadataBackfilled", count(outcomes, ImageAuditOutcome::metadataBackfilled));
        summary.put("anomalies", anomalies);
        summary.put("findings", findings);

        return new ImageIntegrityReport(outcomes.size(), outcomes.size() - failed, failed, summary, outcomes);
    }

    private Map<String, Object> findingMap(ImageAuditOutcome outcome) {
        ImageAuditCandidate candidate = outcome.candidate();
        Map<String, Object> detail = new LinkedHashMap<>();
        if (candidate.assetId() != null) {
            detail.put("assetId", candidate.assetId().toString());
        }
        putIfPresent(detail, "provider", candidate.provider());
        putIfPresent(detail, "sourceKey", candidate.sourceKey());
        detail.put("ownerCount", candidate.ownerCount());
        detail.put("ownerType", candidate.ownerType().name());
        detail.put("ownerId", candidate.ownerId().toString());
        putIfPresent(detail, "label", candidate.ownerLabel());
        putIfPresent(detail, "countryCode", candidate.countryCode());
        putIfPresent(detail, "source", candidate.source());
        putIfPresent(detail, "sourceIndicator", candidate.sourceIndicator());
        putIfPresent(detail, "objectPath", outcome.objectPath());
        detail.put("validationStatus", outcome.validation().status().name());
        detail.put("storageMissing", outcome.storageMissing());
        detail.put("restored", outcome.restored());
        detail.put("metadataBackfilled", outcome.metadataBackfilled());
        detail.put("repairFailed", outcome.repairFailure() != null);
        putIfPresent(detail, "reason", firstNonBlank(outcome.repairFailure(), outcome.validation().reason()));
        detail.put("metadataChanges", outcome.metadataChanges());
        detail.put("storedMetadata", storedMetadataMap(candidate));
        detail.put("currentMetadata", outcome.currentMetadata());
        return detail;
    }

    private List<String> compareMetadata(ImageAuditCandidate candidate, ImageMetadata current) {
        if (current == null) {
            return List.of();
        }
        List<String> changes = new ArrayList<>();
        if (changed(candidate.imageLicenseLabel(), current.licenseLabel())
                || changedUrl(candidate.imageLicenseUrl(), current.licenseUrl())) {
            changes.add("LICENSE");
        }
        if (changed(candidate.imageAuthorName(), current.authorName())
                || changed(candidate.imageAttributionText(), current.attributionText())) {
            changes.add("ATTRIBUTION");
        }
        if (changedUrl(candidate.imageSourcePageUrl(), current.sourcePageUrl())
                || changedUrl(candidate.originalImageUrl(), current.originalImageUrl())) {
            changes.add("SOURCE_URL");
        }
        if (StringUtils.hasText(candidate.sourceSha1())
                && StringUtils.hasText(current.sourceSha1())
                && !candidate.sourceSha1().equalsIgnoreCase(current.sourceSha1())) {
            changes.add("SOURCE_CONTENT");
        }
        return changes;
    }

    private ImageMetadata metadataForAutomaticUpdate(
            ImageAuditCandidate candidate,
            ImageMetadata current) {
        boolean contentChanged = StringUtils.hasText(candidate.sourceSha1())
                && StringUtils.hasText(current.sourceSha1())
                && !candidate.sourceSha1().equalsIgnoreCase(current.sourceSha1());
        if (!contentChanged) {
            return current;
        }
        return new ImageMetadata(
                candidate.originalImageUrl(),
                current.sourcePageUrl(),
                current.authorName(),
                current.attributionText(),
                current.licenseLabel(),
                current.licenseUrl(),
                candidate.recoverySourceUrl(),
                candidate.sourceSha1(),
                current.mimeType(),
                current.width(),
                current.height());
    }

    private void applyLifecycle(
            ImageAuditOutcome outcome,
            Map<Long, Integer> validationFailures,
            Set<Long> quarantinedResources) {
        ImageAuditCandidate candidate = outcome.candidate();
        ImageSourceValidation lifecycleValidation = lifecycleValidation(outcome);
        int consecutiveFailures = validationFailures.computeIfAbsent(
                candidate.assetId(),
                ignored -> imageAssetRepository.recordValidation(candidate, lifecycleValidation));
        List<String> resourceFindings = activeResourceFindingCodes(outcome);
        List<String> renditionFindings = activeRenditionFindingCodes(outcome);
        Map<String, Object> details = findingMap(outcome);
        resourceFindings.forEach(code -> imageAssetRepository.upsertFinding(
                candidate.assetId(), null, code, findingSeverity(code), details));
        renditionFindings.forEach(code -> imageAssetRepository.upsertFinding(
                candidate.assetId(), candidate.renditionId(), code, findingSeverity(code), details));
        if (candidate.renditionId() == null) {
            List<String> combinedFindings = new ArrayList<>(resourceFindings);
            combinedFindings.addAll(renditionFindings);
            imageAssetRepository.resolveFindings(candidate.assetId(), null, combinedFindings);
        } else {
            imageAssetRepository.resolveFindings(candidate.assetId(), null, resourceFindings);
            imageAssetRepository.resolveFindings(
                    candidate.assetId(), candidate.renditionId(), renditionFindings);
        }

        boolean restricted = outcome.validation().status() == ImageSourceValidationStatus.RESTRICTED_LICENSE;
        boolean missing = outcome.validation().status() == ImageSourceValidationStatus.SOURCE_MISSING
                && consecutiveFailures >= 3;
        boolean unrecoverableStorage = outcome.storageMissing()
                && !outcome.restored()
                && outcome.repairFailure() != null
                && consecutiveFailures >= 3;
        boolean unsafeContentDrift = outcome.storageMissing()
                && outcome.metadataChanges().contains("SOURCE_CONTENT");
        if ((!restricted && !missing && !unrecoverableStorage && !unsafeContentDrift)
                || !quarantinedResources.add(candidate.assetId())) {
            return;
        }

        List<ChallengeHintReference> challengeReferences = imageAssetRepository.findChallengeReferences(
                candidate.assetId(), restricted || unsafeContentDrift);
        imageAssetRepository.quarantine(
                candidate,
                firstNonBlank(outcome.repairFailure(), outcome.validation().reason(), "Image validation failed"),
                restricted || unsafeContentDrift);
        challengeReferences.forEach(reference -> reroll(reference, candidate.assetId()));
    }

    private ImageSourceValidation lifecycleValidation(ImageAuditOutcome outcome) {
        if (outcome.storageMissing()
                && !outcome.restored()
                && outcome.repairFailure() != null
                && outcome.validation().status() != ImageSourceValidationStatus.RESTRICTED_LICENSE
                && outcome.validation().status() != ImageSourceValidationStatus.SOURCE_MISSING) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    outcome.repairFailure());
        }
        return outcome.validation();
    }

    private List<String> activeResourceFindingCodes(ImageAuditOutcome outcome) {
        List<String> codes = new ArrayList<>();
        ImageSourceValidationStatus status = outcome.validation().status();
        if (status != ImageSourceValidationStatus.AVAILABLE
                && status != ImageSourceValidationStatus.UNSUPPORTED) {
            codes.add(status.name());
        }
        outcome.metadataChanges().stream()
                .map(change -> "METADATA_" + change)
                .forEach(codes::add);
        return List.copyOf(codes);
    }

    private List<String> activeRenditionFindingCodes(ImageAuditOutcome outcome) {
        return outcome.repairFailure() == null
                ? List.of()
                : List.of("STORAGE_REPAIR_FAILED");
    }

    private String findingSeverity(String code) {
        if ("RESTRICTED_LICENSE".equals(code) || "SOURCE_MISSING".equals(code)) {
            return "CRITICAL";
        }
        if ("METADATA_SOURCE_CONTENT".equals(code)) {
            return "WARNING";
        }
        return code.startsWith("METADATA_") ? "INFO" : "WARNING";
    }

    private void reroll(ChallengeHintReference reference, Long assetId) {
        try {
            challengeRerollService.rerollHint(
                    reference.challengeId(), reference.countryOrder(), reference.hintOrder());
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to reroll quarantined image asset {} in challenge {} at {}/{}: {}",
                    assetId,
                    reference.challengeId(),
                    reference.countryOrder(),
                    reference.hintOrder(),
                    exception.getMessage());
        }
    }

    private boolean hasBackfillableMetadata(ImageAuditCandidate candidate, ImageMetadata current) {
        if (current == null) {
            return false;
        }
        return missingWhenAvailable(candidate.originalImageUrl(), current.originalImageUrl())
                || missingWhenAvailable(candidate.imageSourcePageUrl(), current.sourcePageUrl())
                || missingWhenAvailable(candidate.imageAuthorName(), current.authorName())
                || missingWhenAvailable(candidate.imageAttributionText(), current.attributionText())
                || missingWhenAvailable(candidate.imageLicenseLabel(), current.licenseLabel())
                || missingWhenAvailable(candidate.imageLicenseUrl(), current.licenseUrl());
    }

    private boolean missingWhenAvailable(String stored, String current) {
        return !StringUtils.hasText(stored) && StringUtils.hasText(current);
    }

    private boolean changed(String stored, String current) {
        return StringUtils.hasText(stored)
                && StringUtils.hasText(current)
                && !normalize(stored).equals(normalize(current));
    }

    private boolean changedUrl(String stored, String current) {
        return StringUtils.hasText(stored)
                && StringUtils.hasText(current)
                && !normalizeUrl(stored).equals(normalizeUrl(current));
    }

    private String normalize(String value) {
        String cleaned = ImageRightsSupport.cleanText(value);
        return cleaned != null ? cleaned.toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUrl(String value) {
        String withoutFragment = value.trim().replaceFirst("#.*$", "");
        int queryStart = withoutFragment.indexOf('?');
        String baseUrl = queryStart >= 0 ? withoutFragment.substring(0, queryStart) : withoutFragment;
        String query = queryStart >= 0 ? withoutFragment.substring(queryStart + 1) : "";
        String normalizedQuery = Arrays.stream(query.split("&"))
                .filter(StringUtils::hasText)
                .filter(parameter -> !parameter.regionMatches(true, 0, "utm_", 0, 4))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining("&"));
        String normalized = baseUrl.replaceAll("/+$", "")
                + (normalizedQuery.isEmpty() ? "" : "?" + normalizedQuery);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String deterministicObjectPath(ImageAuditCandidate candidate) {
        String source = safePathSegment(candidate.source(), "unknown-source");
        String country = safePathSegment(candidate.countryCode(), "unknown-country");
        String owner = candidate.ownerType().name().toLowerCase(Locale.ROOT) + "-" + candidate.ownerId();
        String prefix = "AI_GENERATED".equals(candidate.source()) ? "ai" : "repair/" + source;
        return prefix + "/" + country + "/" + owner;
    }

    private String safePathSegment(String value, String fallback) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
    }

    private ImageMetadata safeMetadata(ImageSourceValidation validation) {
        return validation.status() == ImageSourceValidationStatus.AVAILABLE ? validation.metadata() : null;
    }

    private Map<String, Object> storedMetadataMap(ImageAuditCandidate candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "originalImageUrl", candidate.originalImageUrl());
        putIfPresent(metadata, "sourcePageUrl", candidate.imageSourcePageUrl());
        putIfPresent(metadata, "authorName", candidate.imageAuthorName());
        putIfPresent(metadata, "attributionText", candidate.imageAttributionText());
        putIfPresent(metadata, "licenseLabel", candidate.imageLicenseLabel());
        putIfPresent(metadata, "licenseUrl", candidate.imageLicenseUrl());
        return metadata;
    }

    private Map<String, Object> metadataMap(ImageMetadata metadata) {
        if (metadata == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "originalImageUrl", metadata.originalImageUrl());
        putIfPresent(result, "sourcePageUrl", metadata.sourcePageUrl());
        putIfPresent(result, "authorName", metadata.authorName());
        putIfPresent(result, "attributionText", metadata.attributionText());
        putIfPresent(result, "licenseLabel", metadata.licenseLabel());
        putIfPresent(result, "licenseUrl", metadata.licenseUrl());
        putIfPresent(result, "downloadUrl", metadata.downloadUrl());
        putIfPresent(result, "sourceSha1", metadata.sourceSha1());
        putIfPresent(result, "mimeType", metadata.mimeType());
        if (metadata.width() != null) {
            result.put("width", metadata.width());
        }
        if (metadata.height() != null) {
            result.put("height", metadata.height());
        }
        return result;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private int count(List<ImageAuditOutcome> outcomes, Predicate<ImageAuditOutcome> predicate) {
        return (int) outcomes.stream().filter(predicate).count();
    }

    private int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private OperationBatchLog startBatch(OperationTriggerType triggerType, String operationId) {
        OperationBatchLog batchLog = OperationBatchLog.start(OperationType.IMAGE_MAINTENANCE, triggerType);
        batchLog.setSummary(Map.of("operationId", operationId, "mode", "IMAGE_INTEGRITY_AUDIT"));
        return batchLogRepository.save(batchLog);
    }

    private ImageStorageService requireStorage() {
        return imageStorageService.orElseThrow(() -> new IllegalStateException("Image storage is disabled"));
    }
}
