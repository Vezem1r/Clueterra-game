package io.clueterra.questions.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.storage.ImageDownloadResult;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ImageAssetRepository {

    private static final String FIND_AUDIT_CANDIDATES_SQL = SqlLoader.load(ImageAssetRepository.class, "findAuditCandidates");
    private static final String REGISTER_REPAIRED_ASSET_SQL = SqlLoader.load(ImageAssetRepository.class, "registerRepairedAsset");
    private static final String SUPERSEDE_RENDITION_SQL = SqlLoader.load(ImageAssetRepository.class, "supersedeRendition");
    private static final String MARK_ASSET_AVAILABLE_SQL = SqlLoader.load(ImageAssetRepository.class, "markAssetAvailable");
    private static final String SYNC_ACTIVE_CHALLENGE_METADATA_SQL = SqlLoader.load(ImageAssetRepository.class, "syncActiveChallengeMetadata");
    private static final String RECORD_VALIDATION_SQL = SqlLoader.load(ImageAssetRepository.class, "recordValidation");
    private static final String UPSERT_FINDING_SQL = SqlLoader.load(ImageAssetRepository.class, "upsertFinding");
    private static final String RESOLVE_FINDINGS_SQL = SqlLoader.load(ImageAssetRepository.class, "resolveFindings");
    private static final String FIND_CHALLENGE_REFERENCES_SQL = SqlLoader.load(ImageAssetRepository.class, "findChallengeReferences");
    private static final String ACQUIRE_MAINTENANCE_LEASE_SQL =
            SqlLoader.load(ImageAssetRepository.class, "acquireMaintenanceLease");
    private static final String RELEASE_MAINTENANCE_LEASE_SQL =
            SqlLoader.load(ImageAssetRepository.class, "releaseMaintenanceLease");
    private static final String RECORD_STORAGE_CHECK_SQL =
            SqlLoader.load(ImageAssetRepository.class, "recordStorageCheck");
    private static final String RECORD_RENDITION_CONTENT_SQL =
            SqlLoader.load(ImageAssetRepository.class, "recordRenditionContent");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<ImageAuditCandidate> findAuditCandidates() {
        return jdbc.query(FIND_AUDIT_CANDIDATES_SQL, Map.of(), (rs, rowNum) -> mapCandidate(rs));
    }

    public boolean tryAcquireMaintenanceLease(String leaseName, String ownerId, Duration duration) {
        return !jdbc.queryForList(
                ACQUIRE_MAINTENANCE_LEASE_SQL,
                new MapSqlParameterSource()
                        .addValue("leaseName", leaseName)
                        .addValue("ownerId", ownerId)
                        .addValue("leaseSeconds", duration.toSeconds()),
                String.class).isEmpty();
    }

    public void releaseMaintenanceLease(String leaseName, String ownerId) {
        jdbc.update(
                RELEASE_MAINTENANCE_LEASE_SQL,
                new MapSqlParameterSource()
                        .addValue("leaseName", leaseName)
                        .addValue("ownerId", ownerId));
    }

    public void recordStorageCheck(Long renditionId, boolean present) {
        if (renditionId == null) {
            return;
        }
        jdbc.update(
                RECORD_STORAGE_CHECK_SQL,
                new MapSqlParameterSource()
                        .addValue("renditionId", renditionId)
                        .addValue("present", present));
    }

    public void recordRenditionContent(
            String storageBucket,
            ImageDownloadResult downloadResult) {
        if (!downloadResult.success() || downloadResult.objectPath() == null) {
            return;
        }
        jdbc.update(
                RECORD_RENDITION_CONTENT_SQL,
                new MapSqlParameterSource()
                        .addValue("storageBucket", storageBucket)
                        .addValue("objectPath", downloadResult.objectPath())
                        .addValue("byteSize", downloadResult.byteSize())
                        .addValue("contentSha256", downloadResult.contentSha256())
                        .addValue("contentType", downloadResult.contentType()));
    }

    @Transactional
    public void updateAfterRepair(ImageAuditCandidate candidate, String imageUrl, ImageMetadata metadata) {
        ImageMetadata effectiveMetadata = metadata != null
                ? metadata
                : new ImageMetadata(candidate.recoverySourceUrl(), null, null, null, null, null);
        if (candidate.assetId() == null) {
            throw new IllegalStateException("Image repair candidate has no canonical resource identity");
        }
        MapSqlParameterSource canonicalParams = metadataParams(candidate, imageUrl, effectiveMetadata);
        RegisteredImageAsset registered = jdbc.query(REGISTER_REPAIRED_ASSET_SQL, canonicalParams,
                        (rs, rowNum) -> new RegisteredImageAsset(
                                rs.getObject("image_resource_id", Long.class),
                                rs.getObject("previous_rendition_id", Long.class)))
                .stream()
                .findFirst()
                .filter(asset -> asset.resourceId() != null)
                .orElseThrow(() -> new IllegalStateException(
                        "Image repair registered no canonical resource " + candidate.assetId()));

        if (registered.previousRenditionId() != null && imageUrl != null) {
            jdbc.update(SUPERSEDE_RENDITION_SQL, new MapSqlParameterSource()
                    .addValue("renditionId", registered.previousRenditionId())
                    .addValue("objectPath", imageUrl));
        }
        jdbc.update(MARK_ASSET_AVAILABLE_SQL, new MapSqlParameterSource("assetId", registered.resourceId()));
        jdbc.update(SYNC_ACTIVE_CHALLENGE_METADATA_SQL, canonicalParams);
    }

    public int recordValidation(ImageAuditCandidate candidate, ImageSourceValidation validation) {
        if (candidate.assetId() == null) {
            return candidate.consecutiveFailures();
        }
        Integer failures = jdbc.queryForObject(
                RECORD_VALIDATION_SQL,
                new MapSqlParameterSource()
                        .addValue("assetId", candidate.assetId())
                        .addValue("validationStatus", validation.status().name())
                        .addValue("reason", validation.reason()),
                Integer.class);
        return failures != null ? failures : candidate.consecutiveFailures();
    }

    public void upsertFinding(
            Long assetId,
            Long renditionId,
            String findingCode,
            String severity,
            Map<String, Object> details) {
        if (assetId == null) {
            return;
        }
        try {
            jdbc.update(
                    UPSERT_FINDING_SQL,
                    new MapSqlParameterSource()
                            .addValue("assetId", assetId)
                            .addValue("renditionId", renditionId)
                            .addValue("findingCode", findingCode)
                            .addValue("severity", severity)
                            .addValue("details", objectMapper.writeValueAsString(details)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist image asset finding", exception);
        }
    }

    public void resolveFindings(Long assetId, Long renditionId, List<String> activeFindingCodes) {
        if (assetId == null) {
            return;
        }
        List<String> effectiveCodes = activeFindingCodes.isEmpty()
                ? List.of("__NO_ACTIVE_FINDINGS__")
                : activeFindingCodes;
        jdbc.update(
                RESOLVE_FINDINGS_SQL,
                new MapSqlParameterSource()
                        .addValue("assetId", assetId)
                        .addValue("renditionId", renditionId)
                        .addValue("activeFindingCodes", effectiveCodes));
    }

    public void quarantine(
            ImageAuditCandidate candidate,
            String reason,
            boolean permanent) {
        if (candidate.assetId() == null) {
            return;
        }
        jdbc.queryForObject(
                "SELECT cl_media_schema.quarantine_image_resource(:assetId, :reason, :permanent)",
                new MapSqlParameterSource()
                        .addValue("assetId", candidate.assetId())
                        .addValue("reason", reason)
                        .addValue("permanent", permanent),
                Integer.class);
    }

    public List<ChallengeHintReference> findChallengeReferences(Long assetId, boolean includeToday) {
        return jdbc.query(
                FIND_CHALLENGE_REFERENCES_SQL,
                new MapSqlParameterSource()
                        .addValue("assetId", assetId)
                        .addValue("includeToday", includeToday),
                (resultSet, rowNumber) -> new ChallengeHintReference(
                        resultSet.getLong("challenge_id"),
                        resultSet.getInt("country_order"),
                        resultSet.getInt("hint_order")));
    }

    private MapSqlParameterSource metadataParams(
            ImageAuditCandidate candidate,
            String imageUrl,
            ImageMetadata metadata) {
        return new MapSqlParameterSource()
                .addValue("assetId", candidate.assetId())
                .addValue("renditionId", candidate.renditionId())
                .addValue("storageBucket", candidate.storageBucket())
                .addValue("objectPath", imageUrl)
                .addValue("originalImageUrl", metadata.originalImageUrl())
                .addValue("downloadUrl", metadata.downloadUrl())
                .addValue("imageSourcePageUrl", metadata.sourcePageUrl())
                .addValue("imageAuthorName", metadata.authorName())
                .addValue("imageAttributionText", metadata.attributionText())
                .addValue("imageLicenseLabel", metadata.licenseLabel())
                .addValue("imageLicenseUrl", metadata.licenseUrl())
                .addValue("sourceSha1", metadata.sourceSha1())
                .addValue("mimeType", metadata.mimeType())
                .addValue("width", metadata.width())
                .addValue("height", metadata.height());
    }

    private ImageAssetOwnerType resolveOwnerType(String value) {
        if (value == null || value.isBlank()) {
            return ImageAssetOwnerType.RESOURCE;
        }
        try {
            return ImageAssetOwnerType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            log.warn("Unknown image owner type '{}'; auditing it as RESOURCE", value);
            return ImageAssetOwnerType.RESOURCE;
        }
    }

    private ImageAuditCandidate mapCandidate(ResultSet rs) throws SQLException {
        return new ImageAuditCandidate(
                resolveOwnerType(rs.getString("owner_type")),
                rs.getLong("owner_id"),
                rs.getString("owner_label"),
                rs.getString("country_code"),
                rs.getString("source"),
                rs.getString("source_indicator"),
                rs.getString("image_url"),
                rs.getString("original_image_url"),
                rs.getString("image_source_page_url"),
                rs.getString("image_author_name"),
                rs.getString("image_attribution_text"),
                rs.getString("image_license_label"),
                rs.getString("image_license_url"),
                rs.getString("recovery_source_url"),
                rs.getLong("asset_id"),
                rs.getObject("rendition_id", Long.class),
                rs.getString("provider"),
                rs.getString("source_key"),
                rs.getString("storage_bucket"),
                rs.getString("object_path"),
                rs.getString("source_sha1"),
                rs.getString("lifecycle_status"),
                rs.getInt("consecutive_failures"),
                rs.getInt("owner_count"));
    }
}
