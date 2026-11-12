package io.clueterra.api.admin.hints.repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.hints.dto.CreateHintRequest;
import io.clueterra.api.admin.hints.dto.HintAdminDto;
import io.clueterra.api.admin.hints.dto.HintImageUpdate;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.database.SortDirection;
import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.storage.StorageService;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminHintJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageService storageService;

    private static final String BASE_SELECT = SqlLoader.load(AdminHintJdbcRepository.class, "baseSelect");
    private static final String BASE_COUNT = SqlLoader.load(AdminHintJdbcRepository.class, "baseCount");
    private static final String UPDATED_SELECT = SqlLoader.load(AdminHintJdbcRepository.class, "updatedSelect");
    private static final String SET_ACTIVE = SqlLoader.load(AdminHintJdbcRepository.class, "setActive") + UPDATED_SELECT;
    private static final String UPDATE_IMAGE = SqlLoader.load(AdminHintJdbcRepository.class, "updateImage") + UPDATED_SELECT;
    private static final String CLEAR_IMAGE_FIELDS = SqlLoader.load(AdminHintJdbcRepository.class, "clearImageFields") + UPDATED_SELECT;
    private static final String INSERT_SQL = SqlLoader.load(AdminHintJdbcRepository.class, "insert");
    private static final String CREATE_HINT_SUPPRESSION = SqlLoader.load(AdminHintJdbcRepository.class, "createHintSuppression");
    private static final String CREATE_HINT_IMAGE_SUPPRESSION = SqlLoader.load(AdminHintJdbcRepository.class, "createHintImageSuppression");
    private static final String RESOLVE_SUPPRESSION = SqlLoader.load(AdminHintJdbcRepository.class, "resolveSuppression");

    private static final RowMapper<HintAdminDto> ROW_MAPPER = new DataClassRowMapper<>(HintAdminDto.class);

    public Page<HintAdminDto> findAll(String search, List<Long> countryIds, String category, String hintType, String source, Boolean active, Integer difficultyMin, Integer difficultyMax, Integer sourceYear, Boolean hasImage, String sortBy, String sortDir, Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .ilike("LOWER(h.hint_text) LIKE :search OR LOWER(c.name_en) LIKE :search OR LOWER(c.iso_alpha2) LIKE :search OR LOWER(COALESCE(h.source_indicator,'')) LIKE :search", "search", search)
                .in("h.country_id", "countryIds", countryIds)
                .eq("h.category", "category", category)
                .eq("h.hint_type", "hintType", hintType)
                .eq("h.source", "source", source)
                .bool("h.is_active", "active", active)
                .gteInt("h.difficulty", "diffMin", difficultyMin)
                .lteInt("h.difficulty", "diffMax", difficultyMax)
                .eqInt("h.source_year", "sourceYear", sourceYear)
                .notNull("h.image_resource_id", hasImage);

        Long total = jdbc.queryForObject(BASE_COUNT + f.whereClause(), f.params(), Long.class);
        if (total == null || total == 0) return Page.empty(pageable);

        StringBuilder sql = new StringBuilder(BASE_SELECT).append(f.whereClause());
        appendOrderBy(sql, sortBy, sortDir);

        if (pageable.isPaged()) {
            sql.append(" LIMIT :limit OFFSET :offset");
            f.params().addValue("limit", pageable.getPageSize());
            f.params().addValue("offset", pageable.getOffset());
        }

        return new PageImpl<>(jdbc.query(sql.toString(), f.params(), ROW_MAPPER).stream().map(this::resolve).toList(), pageable, total);
    }

    public Optional<HintAdminDto> findById(Long id) {
        var results = jdbc.query(BASE_SELECT + " WHERE h.id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(resolve(results.getFirst()));
    }

    public Optional<HintAdminDto> update(Long id, String hintText, Integer difficulty, BigDecimal qualityScore, Boolean isActive) {
        StringBuilder sql = new StringBuilder("UPDATE hints SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean hasUpdates = false;

        if (hintText != null) {
            sql.append("hint_text = :hintText, editorial_text_locked = TRUE, review_retry_after_at = NULL, "
                    + "content_revision_hash = :revisionHash, review_status = 'STALE', "
                    + "last_reviewed_at = NULL, last_review_run_id = NULL");
            params.addValue("hintText", hintText);
            params.addValue("revisionHash", sha256(hintText));
            hasUpdates = true;
        }
        if (difficulty != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("difficulty = :difficulty");
            params.addValue("difficulty", difficulty);
            hasUpdates = true;
        }
        if (qualityScore != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("quality_score = :qualityScore");
            params.addValue("qualityScore", qualityScore);
            hasUpdates = true;
        }
        if (isActive != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("is_active = :isActive");
            params.addValue("isActive", isActive);
            hasUpdates = true;
        }

        if (!hasUpdates) return findById(id);

        sql.append(", updated_at = :now WHERE id = :id");
        params.addValue("now", LocalDateTime.now());

        String cte = "WITH updated AS (" + sql + " RETURNING *) " + UPDATED_SELECT;
        return jdbc.query(cte, params, ROW_MAPPER).stream().findFirst().map(this::resolve);
    }

    public Optional<HintAdminDto> setActive(Long id, boolean active) {
        var params = new MapSqlParameterSource().addValue("id", id).addValue("active", active).addValue("now", LocalDateTime.now());
        return jdbc.query(SET_ACTIVE, params, ROW_MAPPER).stream().findFirst().map(this::resolve);
    }

    public Optional<HintAdminDto> updateImage(Long id, HintImageUpdate image) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("imageUrl", image.imageUrl())
                .addValue("originalImageUrl", image.originalImageUrl())
                .addValue("imageSourcePageUrl", image.imageSourcePageUrl())
                .addValue("imageAuthorName", image.imageAuthorName())
                .addValue("imageAttributionText", image.imageAttributionText())
                .addValue("imageLicenseLabel", image.imageLicenseLabel())
                .addValue("imageLicenseUrl", image.imageLicenseUrl())
                .addValue("contentRevisionHash", image.contentRevisionHash())
                .addValue("now", LocalDateTime.now());
        return jdbc.query(UPDATE_IMAGE, params, ROW_MAPPER).stream().findFirst().map(this::resolve);
    }

    public Optional<HintAdminDto> clearImageFields(Long id) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("now", LocalDateTime.now());
        return jdbc.query(CLEAR_IMAGE_FIELDS, params, ROW_MAPPER).stream().findFirst().map(this::resolve);
    }

    public boolean softDelete(Long id) {
        var params = new MapSqlParameterSource().addValue("id", id).addValue("now", LocalDateTime.now());
        return jdbc.update("UPDATE hints SET is_active = false, updated_at = :now WHERE id = :id", params) > 0;
    }

    public void createIdentitySuppression(Long id, String action, String reason) {
        jdbc.update(
                CREATE_HINT_SUPPRESSION,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("action", action)
                        .addValue("reason", reason));
    }

    public void createImageSuppression(Long id, String reason) {
        jdbc.update(
                CREATE_HINT_IMAGE_SUPPRESSION,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("reason", reason));
    }

    public void resolveIdentitySuppression(Long id) {
        jdbc.update(RESOLVE_SUPPRESSION, new MapSqlParameterSource("id", id));
    }

    public long count() {
        Long r = jdbc.queryForObject("SELECT COUNT(*) FROM hints", new MapSqlParameterSource(), Long.class);
        return r != null ? r : 0;
    }

    public long countByIsActive(boolean isActive) {
        Long r = jdbc.queryForObject("SELECT COUNT(*) FROM hints WHERE is_active = :active", new MapSqlParameterSource("active", isActive), Long.class);
        return r != null ? r : 0;
    }

    public List<String> findDistinctCategories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM hints ORDER BY category", new MapSqlParameterSource(), String.class);
    }

    public List<String> findDistinctHintTypes() {
        return jdbc.queryForList("SELECT DISTINCT hint_type FROM hints ORDER BY hint_type", new MapSqlParameterSource(), String.class);
    }

    public List<String> findDistinctSources() {
        return jdbc.queryForList("SELECT DISTINCT source FROM hints ORDER BY source", new MapSqlParameterSource(), String.class);
    }

    public List<Integer> findDistinctSourceYears() {
        return jdbc.queryForList("SELECT DISTINCT source_year FROM hints WHERE source_year IS NOT NULL ORDER BY source_year", new MapSqlParameterSource(), Integer.class);
    }

    public Optional<HintAdminDto> insert(CreateHintRequest request) {
        Long newId = TSID.Factory.getTsid().toLong();
        LocalDateTime now = LocalDateTime.now();
        String contentHash = sha256(request.hintText());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", newId)
                .addValue("countryId", request.countryId())
                .addValue("category", request.category())
                .addValue("hintType", request.hintType())
                .addValue("hintText", request.hintText())
                .addValue("difficulty", request.difficulty())
                .addValue("qualityScore", request.qualityScore())
                .addValue("sourceYear", request.sourceYear())
                .addValue("contentHash", contentHash)
                .addValue("now", now);

        jdbc.update(INSERT_SQL, params);
        return findById(newId);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private HintAdminDto resolve(HintAdminDto dto) {
        return new HintAdminDto(
                dto.id(), dto.countryId(), dto.countryCode(), dto.countryName(),
                dto.category(), dto.hintType(), dto.hintText(),
                storageService.resolveHintAssetUrl(dto.id(), dto.imageUrl()),
                dto.originalImageUrl(),
                dto.imageSourcePageUrl(),
                dto.imageAuthorName(),
                dto.imageAttributionText(),
                dto.imageLicenseLabel(),
                dto.imageLicenseUrl(),
                dto.difficulty(), dto.qualityScore(), dto.source(), dto.sourceIndicator(),
                dto.sourceYear(), dto.ambiguityCount(), dto.timesUsed(), dto.timesCorrect(),
                dto.isActive(), dto.createdAt(), dto.updatedAt(),
                dto.reviewRetryAfterAt(), dto.editorialTextLocked());
    }

    private void appendOrderBy(StringBuilder sql, String sortBy, String sortDir) {
        sql.append(" ORDER BY ");
        if (sortBy != null) {
            switch (sortBy) {
                case "category" -> sql.append("h.category");
                case "hintType" -> sql.append("h.hint_type");
                case "difficulty" -> sql.append("h.difficulty");
                case "qualityScore" -> sql.append("h.quality_score");
                case "source" -> sql.append("h.source");
                case "sourceYear" -> sql.append("h.source_year");
                case "ambiguityCount" -> sql.append("h.ambiguity_count");
                case "timesUsed" -> sql.append("h.times_used");
                case "timesCorrect" -> sql.append("h.times_correct");
                case "isActive" -> sql.append("h.is_active");
                case "sourceIndicator" -> sql.append("h.source_indicator");
                case "country.nameEn" -> sql.append("c.name_en");
                default -> sql.append("h.created_at");
            }
        } else {
            sql.append("h.created_at");
        }

        sql.append(" ").append(SortDirection.resolve(sortDir));
    }
}
