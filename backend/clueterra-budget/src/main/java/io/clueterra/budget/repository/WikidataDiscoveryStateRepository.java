package io.clueterra.budget.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.WikidataCandidate;
import io.clueterra.budget.source.wikidata.WikidataDiscoveryCheckpoint;
import io.clueterra.budget.source.wikidata.WikidataDiscoverySeed;
import io.clueterra.budget.source.wikidata.WikidataEnrichmentResult;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WikidataDiscoveryStateRepository {

  private static final int PROCESSOR_VERSION = 4;

  private static final String START_OR_RESUME =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "startOrResume");
  private static final String FIND_CHECKPOINT =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "findCheckpoint");
  private static final String SAVE_PAGE =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "savePage");
  private static final String LOAD_SEEDS =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "loadSeeds");
  private static final String LOAD_PENDING_SEEDS =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "loadPendingSeeds");
  private static final String LOAD_READY_CANDIDATES =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "loadReadyCandidates");
  private static final String SAVE_ENRICHMENT_BATCH =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "saveEnrichmentBatch");
  private static final String COMPLETE =
      SqlLoader.load(WikidataDiscoveryStateRepository.class, "complete");

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public UUID startOrResume(MegaprojectCategory category) {
    Map<String, Object> parameters =
        Map.of(
            "category", category.name(),
            "rootIds", objectMapper.valueToTree(category.rootIds()).toString(),
            "processorVersion", PROCESSOR_VERSION);
    return jdbc.queryForObject(START_OR_RESUME, parameters, UUID.class);
  }

  public Optional<WikidataDiscoveryCheckpoint> findCheckpoint(
      UUID snapshotId, MegaprojectCategory category, String rootId) {
    Map<String, Object> parameters =
        Map.of(
            "snapshotId", snapshotId,
            "category", category.name(),
            "rootId", rootId);
    return jdbc.query(FIND_CHECKPOINT, parameters, this::mapCheckpoint).stream().findFirst();
  }

  public int savePage(
      UUID snapshotId,
      MegaprojectCategory category,
      String rootId,
      String cursorValue,
      boolean complete,
      List<WikidataDiscoverySeed> seeds) {
    Map<String, Object> parameters =
        Map.of(
            "snapshotId", snapshotId,
            "category", category.name(),
            "rootId", rootId,
            "cursorValue", cursorValue,
            "complete", complete,
            "seeds", objectMapper.valueToTree(seeds).toString());
    Integer buffered = jdbc.queryForObject(SAVE_PAGE, parameters, Integer.class);
    return buffered == null ? 0 : buffered;
  }

  public List<WikidataDiscoverySeed> loadSeeds(UUID snapshotId) {
    return jdbc.query(LOAD_SEEDS, Map.of("snapshotId", snapshotId), this::mapSeed);
  }

  public List<WikidataDiscoverySeed> loadPendingSeeds(UUID snapshotId) {
    return jdbc.query(LOAD_PENDING_SEEDS, Map.of("snapshotId", snapshotId), this::mapSeed);
  }

  public List<WikidataCandidate> loadReadyCandidates(UUID snapshotId) {
    return jdbc.query(
        LOAD_READY_CANDIDATES,
        Map.of("snapshotId", snapshotId),
        (resultSet, ignored) -> deserializeCandidate(resultSet.getString("candidate_payload")));
  }

  public int saveEnrichmentBatch(
      UUID snapshotId, MegaprojectCategory category, List<WikidataEnrichmentResult> results) {
    Map<String, Object> parameters =
        Map.of(
            "snapshotId", snapshotId,
            "category", category.name(),
            "results", objectMapper.valueToTree(results).toString());
    Integer saved = jdbc.queryForObject(SAVE_ENRICHMENT_BATCH, parameters, Integer.class);
    return saved == null ? 0 : saved;
  }

  public void complete(UUID snapshotId, MegaprojectCategory category) {
    Map<String, Object> parameters = Map.of("snapshotId", snapshotId, "category", category.name());
    jdbc.queryForObject(COMPLETE, parameters, Integer.class);
  }

  private WikidataDiscoveryCheckpoint mapCheckpoint(ResultSet resultSet, int ignored)
      throws SQLException {
    return new WikidataDiscoveryCheckpoint(
        resultSet.getObject("snapshot_id", UUID.class),
        resultSet.getString("root_id"),
        resultSet.getString("cursor_value"),
        "COMPLETE".equals(resultSet.getString("status")),
        resultSet.getInt("records_buffered"));
  }

  private WikidataDiscoverySeed mapSeed(ResultSet resultSet, int ignored) throws SQLException {
    return new WikidataDiscoverySeed(
        resultSet.getString("wikidata_id"), resultSet.getInt("sitelink_count"));
  }

  private WikidataCandidate deserializeCandidate(String payload) {
    try {
      return objectMapper.readValue(payload, WikidataCandidate.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid persisted Wikidata candidate payload", exception);
    }
  }
}
