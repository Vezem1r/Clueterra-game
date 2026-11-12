package io.clueterra.api.admin.badges.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.badges.dto.BadgeAdminDto;
import io.clueterra.api.admin.badges.dto.BadgeEvalLogDto;
import io.clueterra.api.admin.badges.dto.BadgeTranslationDto;
import io.clueterra.api.admin.badges.dto.UserBadgeAdminDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminBadgeJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_USER_BADGES = SqlLoader.load(AdminBadgeJdbcRepository.class, "findUserBadges");
    private static final String FIND_EVAL_LOG = SqlLoader.load(AdminBadgeJdbcRepository.class, "findEvalLog");

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "slug", "grant_type", "display_order", "created_at", "updated_at", "owner_count"
    );

    private static final String BASE_SELECT = """
            SELECT b.id, b.slug, b.image_url, b.accent_color_key, b.effect_color_key, b.is_secret, b.is_active,
                   b.grant_type, b.grant_config::TEXT AS grant_config, b.display_order,
                   b.created_at, b.updated_at,
                   COALESCE(ub_count.cnt, 0) AS owner_count,
                   b.category_id, bc.name AS category_name
            FROM badges b
            LEFT JOIN (SELECT badge_id, COUNT(*) AS cnt FROM user_badges GROUP BY badge_id) ub_count
                ON ub_count.badge_id = b.id
            LEFT JOIN badge_categories bc ON bc.id = b.category_id
            """;

    private static final String BASE_COUNT = """
            SELECT COUNT(*) FROM badges b
            """;

    private static final RowMapper<BadgeAdminDto> LIST_ROW_MAPPER = (rs, rowNum) -> new BadgeAdminDto(
            rs.getLong("id"),
            rs.getString("slug"),
            rs.getString("image_url"),
            rs.getString("accent_color_key"),
            rs.getString("effect_color_key"),
            rs.getBoolean("is_secret"),
            rs.getBoolean("is_active"),
            rs.getString("grant_type"),
            rs.getString("grant_config"),
            rs.getInt("display_order"),
            rs.getInt("owner_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            null,
            rs.getObject("category_id", Long.class),
            rs.getString("category_name")
    );

    private static final String INSERT_BADGE = """
            INSERT INTO badges (id, slug, image_url, accent_color_key, effect_color_key, is_secret, is_active, grant_type, grant_config, display_order, category_id, created_at, updated_at)
            VALUES (:id, :slug, NULL, :accentColorKey, :effectColorKey, :isSecret, :isActive, :grantType, :grantConfig::JSONB, :displayOrder, :categoryId, NOW(), NOW())
            """;

    private static final String INSERT_TRANSLATION = """
            INSERT INTO badge_translations (id, badge_id, locale, name, description_unlocked, description_locked)
            VALUES (:id, :badgeId, :locale, :name, :descUnlocked, :descLocked)
            """;

    private static final String GRANT_BADGE = """
            INSERT INTO user_badges (id, user_id, badge_id, granted_by, is_acknowledged)
            VALUES (:id, :userId, :badgeId, :grantedBy, FALSE)
            ON CONFLICT (user_id, badge_id) DO NOTHING
            """;

    public Page<BadgeAdminDto> findAll(String search, String grantType, Boolean isSecret,
                                       Boolean isActive, String sortBy, String sortDir,
                                       int page, int size) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .ilike("LOWER(b.slug) LIKE :search", "search", search)
                .eq("b.grant_type", "grantType", grantType)
                .bool("b.is_secret", "isSecret", isSecret)
                .bool("b.is_active", "isActive", isActive);

        String where = f.whereClause();
        MapSqlParameterSource params = f.params();

        Long total = jdbc.queryForObject(BASE_COUNT + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }

        String sortColumn = ALLOWED_SORT_COLUMNS.contains(sortBy) ? sortBy : "created_at";
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        String sql = BASE_SELECT + where
                + " ORDER BY " + sortColumn + " " + direction
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<BadgeAdminDto> rows = jdbc.query(sql, params, LIST_ROW_MAPPER);

        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(BadgeAdminDto::id).toList();
            Map<Long, Map<String, BadgeTranslationDto>> translationsMap = findTranslationsByBadgeIds(ids);
            rows = rows.stream().map(b -> new BadgeAdminDto(
                    b.id(), b.slug(), b.imageUrl(), b.accentColorKey(), b.effectColorKey(), b.isSecret(), b.isActive(),
                    b.grantType(), b.grantConfig(), b.displayOrder(), b.ownerCount(),
                    b.createdAt(), b.updatedAt(), translationsMap.getOrDefault(b.id(), Map.of()),
                    b.categoryId(), b.categoryName()
            )).toList();
        }

        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }

    public Optional<BadgeAdminDto> findById(Long id) {
        String sql = BASE_SELECT + " WHERE b.id = :id";
        var params = new MapSqlParameterSource("id", id);
        List<BadgeAdminDto> badges = jdbc.query(sql, params, LIST_ROW_MAPPER);
        if (badges.isEmpty()) return Optional.empty();

        BadgeAdminDto badge = badges.getFirst();
        Map<String, BadgeTranslationDto> translations = findTranslationsByBadgeIds(List.of(id))
                .getOrDefault(id, Map.of());
        return Optional.of(new BadgeAdminDto(
                badge.id(), badge.slug(), badge.imageUrl(), badge.accentColorKey(), badge.effectColorKey(), badge.isSecret(), badge.isActive(),
                badge.grantType(), badge.grantConfig(), badge.displayOrder(), badge.ownerCount(),
                badge.createdAt(), badge.updatedAt(), translations,
                badge.categoryId(), badge.categoryName()
        ));
    }

    private Map<Long, Map<String, BadgeTranslationDto>> findTranslationsByBadgeIds(List<Long> badgeIds) {
        String sql = """
                SELECT badge_id, locale, name, description_unlocked, description_locked
                FROM badge_translations WHERE badge_id IN (:badgeIds)
                """;
        var params = new MapSqlParameterSource("badgeIds", badgeIds);
        Map<Long, Map<String, BadgeTranslationDto>> result = new HashMap<>();
        jdbc.query(sql, params, (rs, rowNum) -> {
            Long badgeId = rs.getLong("badge_id");
            result.computeIfAbsent(badgeId, k -> new HashMap<>())
                    .put(rs.getString("locale"), new BadgeTranslationDto(
                            rs.getString("name"),
                            rs.getString("description_unlocked"),
                            rs.getString("description_locked")
                    ));
            return null;
        });
        return result;
    }

    public void insert(Long id, String slug, String accentColorKey, String effectColorKey, boolean isSecret,
                       boolean isActive, String grantType, String grantConfig, int displayOrder, Long categoryId) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("slug", slug)
                .addValue("accentColorKey", accentColorKey)
                .addValue("effectColorKey", effectColorKey)
                .addValue("isSecret", isSecret)
                .addValue("isActive", isActive)
                .addValue("grantType", grantType)
                .addValue("grantConfig", grantConfig)
                .addValue("displayOrder", displayOrder)
                .addValue("categoryId", categoryId);
        jdbc.update(INSERT_BADGE, params);
    }

    public void replaceTranslations(Long badgeId, Map<String, BadgeTranslationDto> translations) {
        jdbc.update("DELETE FROM badge_translations WHERE badge_id = :badgeId",
                new MapSqlParameterSource("badgeId", badgeId));

        SqlParameterSource[] batchParams = translations.entrySet().stream()
                .map(e -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("id", TSID.Factory.getTsid().toLong())
                        .addValue("badgeId", badgeId)
                        .addValue("locale", e.getKey())
                        .addValue("name", e.getValue().name())
                        .addValue("descUnlocked", e.getValue().descriptionUnlocked())
                        .addValue("descLocked", e.getValue().descriptionLocked()))
                .toArray(SqlParameterSource[]::new);
        if (batchParams.length > 0) {
            jdbc.batchUpdate(INSERT_TRANSLATION, batchParams);
        }
    }

    public int update(Long id, String slug, String accentColorKey, String effectColorKey, Boolean isSecret, Boolean isActive,
                      String grantType, String grantConfig, Integer displayOrder, Long categoryId) {
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);

        if (slug != null) {
            sets.add("slug = :slug");
            params.addValue("slug", slug);
        }
        if (accentColorKey != null) {
            sets.add("accent_color_key = :accentColorKey");
            params.addValue("accentColorKey", accentColorKey);
        }
        if (effectColorKey != null) {
            sets.add("effect_color_key = :effectColorKey");
            params.addValue("effectColorKey", effectColorKey);
        }
        if (isSecret != null) {
            sets.add("is_secret = :isSecret");
            params.addValue("isSecret", isSecret);
        }
        if (isActive != null) {
            sets.add("is_active = :isActive");
            params.addValue("isActive", isActive);
        }
        if (grantType != null) {
            sets.add("grant_type = :grantType");
            params.addValue("grantType", grantType);
        }
        if (grantConfig != null) {
            sets.add("grant_config = :grantConfig::JSONB");
            params.addValue("grantConfig", grantConfig);
        }
        if (displayOrder != null) {
            sets.add("display_order = :displayOrder");
            params.addValue("displayOrder", displayOrder);
        }
        if (categoryId != null) {
            sets.add("category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }

        if (sets.isEmpty()) return 0;
        sets.add("updated_at = NOW()");

        String sql = "UPDATE badges SET " + String.join(", ", sets) + " WHERE id = :id";
        return jdbc.update(sql, params);
    }

    public int delete(Long id) {
        return jdbc.update("DELETE FROM badges WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public void updateImageUrl(Long id, String url) {
        jdbc.update("UPDATE badges SET image_url = :url, updated_at = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("url", url));
    }

    public String findImageUrl(Long id) {
        var params = new MapSqlParameterSource("id", id);
        List<String> urls = jdbc.queryForList("SELECT image_url FROM badges WHERE id = :id", params, String.class);
        return urls.isEmpty() ? null : urls.getFirst();
    }

    public boolean existsBySlug(String slug) {
        var params = new MapSqlParameterSource("slug", slug);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM badges WHERE slug = :slug)", params, Boolean.class));
    }

    public boolean existsBySlugExcluding(String slug, Long excludeId) {
        var params = new MapSqlParameterSource("slug", slug).addValue("excludeId", excludeId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM badges WHERE slug = :slug AND id != :excludeId)", params, Boolean.class));
    }

    public List<UserBadgeAdminDto> findUserBadges(Long userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(FIND_USER_BADGES, params, (rs, rowNum) -> new UserBadgeAdminDto(
                rs.getLong("badge_id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("image_url"),
                rs.getString("granted_at"),
                rs.getString("granted_by")
        ));
    }

    public void grantBadge(Long userId, Long badgeId, String grantedBy) {
        var params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("userId", userId)
                .addValue("badgeId", badgeId)
                .addValue("grantedBy", grantedBy);
        jdbc.update(GRANT_BADGE, params);
    }

    public void revokeBadge(Long userId, Long badgeId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("badgeId", badgeId);
        jdbc.update("DELETE FROM user_badges WHERE user_id = :userId AND badge_id = :badgeId", params);
    }

    public void bulkUpdateBadgeOrder(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) return;
        SqlParameterSource[] batch = new SqlParameterSource[orderedIds.size()];
        for (int i = 0; i < orderedIds.size(); i++) {
            batch[i] = new MapSqlParameterSource()
                    .addValue("id", orderedIds.get(i))
                    .addValue("pos", i + 1);
        }
        jdbc.batchUpdate("UPDATE badges SET display_order = :pos, updated_at = NOW() WHERE id = :id", batch);
    }

    public Page<BadgeEvalLogDto> findEvalLog(Long badgeId, int page, int size) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eqLong("badge_id", "badgeId", badgeId);

        String where = f.whereClause();
        MapSqlParameterSource params = f.params();

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM badge_evaluation_log" + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }

        String sql = FIND_EVAL_LOG + where + " ORDER BY started_at DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<BadgeEvalLogDto> rows = jdbc.query(sql, params, (rs, rowNum) -> new BadgeEvalLogDto(
                rs.getLong("id"),
                rs.getLong("badge_id"),
                rs.getString("evaluation_type"),
                rs.getInt("users_evaluated"),
                rs.getInt("users_granted"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null,
                rs.getString("error_message")
        ));
        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }
}
