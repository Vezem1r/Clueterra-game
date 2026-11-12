package io.clueterra.questions.image;

import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MediaGarbageCollectionRepository {

    private static final String MARK_ORPHANS_SQL =
            SqlLoader.load(MediaGarbageCollectionRepository.class, "markOrphans");
    private static final String FIND_DELETION_CANDIDATES_SQL =
            SqlLoader.load(MediaGarbageCollectionRepository.class, "findDeletionCandidates");
    private static final String FIND_REGISTERED_OBJECT_KEYS_SQL =
            SqlLoader.load(MediaGarbageCollectionRepository.class, "findRegisteredObjectKeys");

    private final NamedParameterJdbcTemplate jdbc;

    public int refreshOrphanState() {
        Integer count = jdbc.queryForObject(MARK_ORPHANS_SQL, Map.of(), Integer.class);
        return count != null ? count : 0;
    }

    public List<ImageRenditionDeletionCandidate> findDeletionCandidates(int graceHours) {
        return jdbc.query(
                FIND_DELETION_CANDIDATES_SQL,
                new MapSqlParameterSource("graceHours", graceHours),
                (resultSet, rowNumber) -> new ImageRenditionDeletionCandidate(
                        resultSet.getLong("rendition_id"),
                        resultSet.getString("storage_bucket"),
                        resultSet.getString("object_key")));
    }

    public Set<String> findRegisteredObjectKeys(String bucket) {
        return Set.copyOf(jdbc.queryForList(
                FIND_REGISTERED_OBJECT_KEYS_SQL,
                new MapSqlParameterSource("bucket", bucket),
                String.class));
    }

    public void markDeleted(Long renditionId) {
        jdbc.update(
                "UPDATE cl_media_schema.image_rendition "
                        + "SET storage_status = 'DELETED', updated_at = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", renditionId));
    }
}
