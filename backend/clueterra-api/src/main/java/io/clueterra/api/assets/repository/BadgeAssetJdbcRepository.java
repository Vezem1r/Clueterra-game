package io.clueterra.api.assets.repository;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BadgeAssetJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<String> findImageUrl(long badgeId) {
        return jdbc.queryForList(
                        "SELECT image_url FROM badges WHERE id = :badgeId AND image_url IS NOT NULL",
                        new MapSqlParameterSource("badgeId", badgeId),
                        String.class)
                .stream()
                .findFirst();
    }
}
