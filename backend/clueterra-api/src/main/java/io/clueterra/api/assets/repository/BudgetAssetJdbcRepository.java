package io.clueterra.api.assets.repository;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetAssetJdbcRepository {

    private static final String FIND_CANDIDATE_IMAGE_REFERENCE =
            SqlLoader.load(BudgetAssetJdbcRepository.class, "findCandidateImageReference");
    private static final String FIND_VARIANT_IMAGE_REFERENCE =
            SqlLoader.load(BudgetAssetJdbcRepository.class, "findVariantImageReference");
    private static final String FIND_ADMIN_VARIANT_IMAGE_REFERENCE =
            SqlLoader.load(BudgetAssetJdbcRepository.class, "findAdminVariantImageReference");
    private static final String FIND_PUBLISHED_PROJECT_IMAGE_REFERENCE =
            SqlLoader.load(BudgetAssetJdbcRepository.class, "findPublishedProjectImageReference");

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<BudgetImageReference> findCandidateImageReference(
            String wikidataId, String category, String sourceSha1) {
        return find(
                FIND_CANDIDATE_IMAGE_REFERENCE,
                Map.of(
                        "wikidataId", wikidataId,
                        "category", category,
                        "sourceSha1", sourceSha1));
    }

    public Optional<BudgetImageReference> findVariantImageReference(
            long variantId, BudgetImageRole role) {
        return find(
                FIND_VARIANT_IMAGE_REFERENCE,
                Map.of("variantId", variantId, "role", role.name()));
    }

    public Optional<BudgetImageReference> findAdminVariantImageReference(
            long variantId, BudgetImageRole role) {
        return find(
                FIND_ADMIN_VARIANT_IMAGE_REFERENCE,
                Map.of("variantId", variantId, "role", role.name()));
    }

    public Optional<BudgetImageReference> findPublishedProjectImageReference(
            String wikidataId, String sourceSha1) {
        return find(
                FIND_PUBLISHED_PROJECT_IMAGE_REFERENCE,
                Map.of("wikidataId", wikidataId, "sourceSha1", sourceSha1));
    }

    private Optional<BudgetImageReference> find(String sql, Map<String, ?> parameters) {
        return jdbc
                .query(
                        sql,
                        parameters,
                        (resultSet, rowNumber) ->
                                new BudgetImageReference(
                                        resultSet.getString("object_key"),
                                        resultSet.getString("fallback_image_url"),
                                        resultSet.getString("original_image_url")))
                .stream()
                .findFirst();
    }
}
