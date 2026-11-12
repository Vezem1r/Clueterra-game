package io.clueterra.budget.source.wikidata;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.clueterra.budget.config.ExternalHttpProperties;
import io.clueterra.budget.config.RetryableRequestTag;
import io.clueterra.budget.config.WikidataProperties;
import io.clueterra.budget.exception.ExternalSourceException;
import io.clueterra.budget.exception.MediaWikiMaxLagException;
import io.clueterra.budget.image.WikimediaImageCandidate;
import io.clueterra.budget.project.BudgetContentEvidence;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.BudgetQualityAssessment;
import io.clueterra.budget.project.CandidateRegion;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectCostVerificationStatus;
import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;
import io.clueterra.budget.project.WikidataCandidate;
import io.clueterra.budget.project.WikidataCandidateImage;
import io.clueterra.budget.project.WikidataProjectCost;
import io.clueterra.budget.repository.WikidataDiscoveryStateRepository;
import io.clueterra.budget.service.BudgetContentQualityPolicy;
import io.clueterra.budget.source.exchange.ExchangeRateBasis;
import io.clueterra.budget.source.exchange.ExchangeRateClient;
import io.clueterra.budget.source.exchange.ExchangeRateQuote;
import io.clueterra.budget.source.wikimedia.WikimediaCommonsClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
@RequiredArgsConstructor
@Slf4j
public class WikidataSparqlClient {

  public static final String SOURCE_CODE = "WIKIDATA_DISCOVERY";
  private static final String ENTITY_HTTP_PREFIX = "http://www.wikidata.org/entity/";
  private static final String ENTITY_HTTPS_PREFIX = "https://www.wikidata.org/entity/";
  private static final int MIN_WIKIPEDIA_COST_FALLBACK_SITELINKS = 10;
  private static final String WIKIPEDIA_DESCRIPTION_SOURCE = "Wikipedia contributors";
  private static final String WIKIPEDIA_LICENSE_LABEL = "CC BY-SA 4.0";
  private static final String WIKIPEDIA_LICENSE_URL =
      "https://creativecommons.org/licenses/by-sa/4.0/";
  private static final BigDecimal HALF = new BigDecimal("0.5");

  private final OkHttpClient externalSourceHttpClient;
  private final ObjectMapper objectMapper;
  private final WikidataProperties properties;
  private final ExternalHttpProperties httpProperties;
  private final WikimediaCommonsClient commonsClient;
  private final ExchangeRateClient exchangeRateClient;
  private final CountryRegionResolver regionResolver;
  private final WikidataCategoryRelevancePolicy categoryRelevancePolicy;
  private final WikipediaInfoboxCostExtractor infoboxCostExtractor;
  private final WikipediaInfoboxSupplementExtractor infoboxSupplementExtractor;
  private final WikidataDiscoveryStateRepository discoveryStateRepository;
  private final BudgetContentQualityPolicy contentQualityPolicy;

  public WikidataCategoryDiscoveryResult discover(
      MegaprojectCategory category,
      Runnable heartbeat,
      Consumer<String> progress,
      Consumer<List<WikidataCandidate>> readyCandidateConsumer) {
    if (!properties.enabled()) {
      throw new ExternalSourceException("Wikidata discovery is disabled");
    }
    WikidataSeedSnapshot snapshot = discoverSeeds(category, heartbeat);
    List<WikidataDiscoverySeed> seeds = snapshot.seeds();
    List<WikidataDiscoverySeed> pendingSeeds =
        discoveryStateRepository.loadPendingSeeds(snapshot.snapshotId());
    List<WikidataCandidate> candidates =
        new ArrayList<>(discoveryStateRepository.loadReadyCandidates(snapshot.snapshotId()));
    int alreadyProcessed = seeds.size() - pendingSeeds.size();
    if (!candidates.isEmpty()) {
      readyCandidateConsumer.accept(List.copyOf(candidates));
    }
    if (alreadyProcessed > 0) {
      String resumeMessage =
          category
              + " metadata: resuming after "
              + alreadyProcessed
              + "/"
              + seeds.size()
              + " persisted results, "
              + candidates.size()
              + " cost-ready";
      log.info(resumeMessage);
      progress.accept(resumeMessage);
    }
    if (pendingSeeds.isEmpty()) {
      return new WikidataCategoryDiscoveryResult(
          snapshot.snapshotId(), seeds.size(), List.copyOf(candidates), 0, List.of());
    }
    int batchSize = properties.entityBatchSize();
    int alreadyProcessedPercent = alreadyProcessed * 100 / seeds.size();
    int nextProgressPercent = Math.max(10, (alreadyProcessedPercent / 10 + 1) * 10);
    int processedThisRun = 0;
    List<String> deferredBatches = new ArrayList<>();
    for (int from = 0; from < pendingSeeds.size(); from += batchSize) {
      int to = Math.min(from + batchSize, pendingSeeds.size());
      int batchNumber = from / batchSize + 1;
      List<WikidataDiscoverySeed> batch = List.copyOf(pendingSeeds.subList(from, to));
      WikidataBatchOutcome outcome =
          processEnrichmentBatch(
              snapshot.snapshotId(),
              category,
              batch,
              "batch " + batchNumber,
              readyCandidateConsumer,
              heartbeat,
              progress);
      candidates.addAll(outcome.candidates());
      processedThisRun += outcome.processedCount();
      deferredBatches.addAll(outcome.failures());
      int processed = alreadyProcessed + processedThisRun;
      int percent = processed * 100 / seeds.size();
      if (percent >= nextProgressPercent || processed == seeds.size()) {
        log.info(
            "Wikidata {} metadata enrichment: {}/{} ({}%), {} cost-ready",
            category, processed, seeds.size(), percent, candidates.size());
        progress.accept(
            category
                + " metadata: "
                + processed
                + "/"
                + seeds.size()
                + " ("
                + percent
                + "%), "
                + candidates.size()
                + " cost-ready");
        nextProgressPercent = (percent / 10 + 1) * 10;
      }
      if (to < pendingSeeds.size()) {
        pauseBetweenRequests();
      }
      heartbeat.run();
    }
    int remaining = discoveryStateRepository.loadPendingSeeds(snapshot.snapshotId()).size();
    return new WikidataCategoryDiscoveryResult(
        snapshot.snapshotId(),
        seeds.size(),
        discoveryStateRepository.loadReadyCandidates(snapshot.snapshotId()),
        remaining,
        summarizedFailures(deferredBatches));
  }

  private WikidataBatchOutcome processEnrichmentBatch(
      UUID snapshotId,
      MegaprojectCategory category,
      List<WikidataDiscoverySeed> seeds,
      String batchName,
      Consumer<List<WikidataCandidate>> readyCandidateConsumer,
      Runnable heartbeat,
      Consumer<String> progress) {
    try {
      WikidataEnrichmentBatch enrichment =
          withBatchRecovery(
              () -> enrichBatch(category, seeds), category + " metadata " + batchName);
      List<WikidataCandidate> ready = enrichment.candidates();
      int saved =
          discoveryStateRepository.saveEnrichmentBatch(snapshotId, category, enrichment.results());
      if (saved != enrichment.results().size()) {
        throw new IllegalStateException(
            category + " metadata " + batchName + " was not fully persisted");
      }
      if (!ready.isEmpty()) {
        readyCandidateConsumer.accept(ready);
      }
      return WikidataBatchOutcome.processed(ready, seeds.size());
    } catch (ExternalSourceException exception) {
      int minimumBatchSize = properties.minimumRecoveryBatchSize();
      if (seeds.size() >= minimumBatchSize * 2) {
        int midpoint = seeds.size() / 2;
        String splitMessage =
            category
                + " metadata "
                + batchName
                + " still failed after retries; splitting "
                + seeds.size()
                + " seeds into "
                + midpoint
                + "+"
                + (seeds.size() - midpoint);
        log.warn("{}: {}", splitMessage, conciseFailure(exception));
        progress.accept(splitMessage);
        WikidataBatchOutcome first =
            processEnrichmentBatch(
                snapshotId,
                category,
                List.copyOf(seeds.subList(0, midpoint)),
                batchName + ".1",
                readyCandidateConsumer,
                heartbeat,
                progress);
        heartbeat.run();
        pauseBetweenRequests();
        WikidataBatchOutcome second =
            processEnrichmentBatch(
                snapshotId,
                category,
                List.copyOf(seeds.subList(midpoint, seeds.size())),
                batchName + ".2",
                readyCandidateConsumer,
                heartbeat,
                progress);
        return first.merge(second);
      }
      String failure = batchName + ": " + conciseFailure(exception);
      log.warn(
          "Wikidata {} metadata {} deferred {} seeds: {}",
          category,
          batchName,
          seeds.size(),
          conciseFailure(exception));
      progress.accept(
          "Deferred "
              + category
              + " metadata "
              + batchName
              + " ("
              + seeds.size()
              + " seeds): "
              + conciseFailure(exception));
      return WikidataBatchOutcome.deferred(seeds.size(), failure);
    }
  }

  private WikidataEnrichmentBatch enrichBatch(
      MegaprojectCategory category, List<WikidataDiscoverySeed> seeds) {
    Set<String> projectIds =
        seeds.stream()
            .map(WikidataDiscoverySeed::wikidataId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, JsonNode> entities =
        fetchEntities(projectIds, "labels|descriptions|sitelinks", true);
    Map<String, List<JsonNode>> factsByProject = fetchProjectFacts(projectIds);
    Map<String, List<WikidataRawMetric>> metricsByProject = fetchProjectMetrics(projectIds);
    Map<String, List<WikidataRawCost>> costsByProject = fetchStructuredCosts(projectIds);
    Set<String> countryIds =
        factsByProject.values().stream()
            .flatMap(List::stream)
            .map(binding -> entityId(value(binding, "country")))
            .filter(countryId -> !isInvalidWikidataId(countryId))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    entities.putAll(fetchEntities(countryIds, "labels", false));

    Map<String, WikidataRejectionReason> rejected = new LinkedHashMap<>();
    List<WikidataRawProject> rawProjects = new ArrayList<>();
    for (WikidataDiscoverySeed seed : seeds) {
      Optional<WikidataRejectionReason> rawFailure =
          rawProjectFailure(seed, factsByProject, entities);
      if (rawFailure.isPresent()) {
        rejected.put(seed.wikidataId(), rawFailure.orElseThrow());
      } else {
        WikidataRawProject project =
            parseRawProject(seed, factsByProject, costsByProject, metricsByProject, entities)
                .orElseThrow();
        Optional<WikidataRejectionReason> cheapFailure =
            cheapScreenFailure(category, project, entities);
        if (cheapFailure.isPresent()) {
          rejected.put(seed.wikidataId(), cheapFailure.orElseThrow());
        } else {
          rawProjects.add(project);
        }
      }
    }
    List<WikidataRawProject> wikipediaEligible = new ArrayList<>();
    rawProjects.forEach(
        project -> {
          if (project.cost() != null
              || project.sitelinkCount() >= MIN_WIKIPEDIA_COST_FALLBACK_SITELINKS) {
            wikipediaEligible.add(project);
          } else {
            rejected.put(project.wikidataId(), WikidataRejectionReason.MISSING_PLAYABLE_COST);
          }
        });
    List<WikidataRawProject> costEnriched = enrichWikipediaData(wikipediaEligible, entities);
    Map<String, WikipediaPageSignals> wikipediaSignals =
        fetchWikipediaSignals(costEnriched, entities);
    Map<String, WikimediaImageCandidate> reusableImages = fetchReusableImages(costEnriched);
    Map<String, WikidataCandidateEvaluation> evaluations =
        costEnriched.stream()
            .collect(
                Collectors.toMap(
                    WikidataRawProject::wikidataId,
                    rawProject ->
                        evaluateCandidate(
                            category, rawProject, entities, wikipediaSignals, reusableImages),
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    List<WikidataEnrichmentResult> results =
        seeds.stream()
            .map(seed -> enrichmentResult(seed.wikidataId(), rejected, evaluations))
            .toList();
    return new WikidataEnrichmentBatch(results);
  }

  private WikidataEnrichmentResult enrichmentResult(
      String wikidataId,
      Map<String, WikidataRejectionReason> rejected,
      Map<String, WikidataCandidateEvaluation> evaluations) {
    WikidataCandidateEvaluation evaluation = evaluations.get(wikidataId);
    if (evaluation != null && evaluation.candidate() != null) {
      return WikidataEnrichmentResult.ready(evaluation.candidate());
    }
    WikidataRejectionReason reason =
        evaluation == null ? rejected.get(wikidataId) : evaluation.rejectionReason();
    return WikidataEnrichmentResult.rejected(
        wikidataId, Objects.requireNonNullElse(reason, WikidataRejectionReason.MISSING_ENTITY));
  }

  private Optional<WikidataRejectionReason> cheapScreenFailure(
      MegaprojectCategory category, WikidataRawProject project, Map<String, JsonNode> entities) {
    JsonNode entity = entities.get(project.wikidataId());
    String name = localizedValue(entity, "labels");
    String description = localizedValue(entity, "descriptions");
    if (name.isBlank() || name.equals(project.wikidataId())) {
      return Optional.of(WikidataRejectionReason.MISSING_NAME);
    }
    if (regionResolver.resolve(project.countryCode()) == CandidateRegion.UNKNOWN) {
      return Optional.of(WikidataRejectionReason.UNKNOWN_REGION);
    }
    if (!categoryRelevancePolicy.isRelevant(category, name, description)) {
      return Optional.of(WikidataRejectionReason.CATEGORY_MISMATCH);
    }
    return Optional.empty();
  }

  private WikidataSeedSnapshot discoverSeeds(MegaprojectCategory category, Runnable heartbeat) {
    UUID snapshotId = discoveryStateRepository.startOrResume(category);
    for (String rootId : category.rootIds()) {
      WikidataDiscoveryCheckpoint checkpoint =
          discoveryStateRepository
              .findCheckpoint(snapshotId, category, rootId)
              .orElseThrow(
                  () ->
                      new ExternalSourceException(
                          "Missing Wikidata discovery checkpoint for " + category + "/" + rootId));
      if (checkpoint.complete()) {
        log.info(
            "Resuming completed Wikidata {} root {} snapshot with {} buffered seeds",
            category,
            rootId,
            checkpoint.recordsBuffered());
        continue;
      }
      String cursor = checkpoint.cursorValue();
      int page = 1;
      while (true) {
        int currentPage = page;
        String currentCursor = cursor;
        JsonNode bindings =
            withBatchRecovery(
                    () ->
                        executeSparql(
                            seedQuery(rootId, currentCursor),
                            "Wikidata " + category + " seed page " + currentPage),
                    category + " seed page " + currentPage)
                .path("results")
                .path("bindings");
        if (!bindings.isArray()) {
          throw new ExternalSourceException("Wikidata returned no seed bindings");
        }
        List<WikidataDiscoverySeed> pageSeeds =
            StreamSupport.stream(bindings.spliterator(), false)
                .map(this::parseSeed)
                .flatMap(Optional::stream)
                .toList();
        boolean complete = pageSeeds.size() < properties.queryPageSize();
        String nextCursor =
            pageSeeds.isEmpty() ? cursor : ENTITY_HTTP_PREFIX + pageSeeds.getLast().wikidataId();
        if (nextCursor.compareTo(cursor) <= 0) {
          if (!complete) {
            throw new ExternalSourceException(
                "Wikidata keyset cursor did not advance for " + rootId);
          }
        }
        int buffered =
            discoveryStateRepository.savePage(
                snapshotId, category, rootId, nextCursor, complete, pageSeeds);
        heartbeat.run();
        cursor = nextCursor;
        if (complete) {
          log.info(
              "Wikidata {} root {} seed discovery complete: {} buffered candidates",
              category,
              rootId,
              buffered);
          break;
        }
        page++;
        pauseBetweenRequests();
      }
    }
    List<WikidataDiscoverySeed> seeds = discoveryStateRepository.loadSeeds(snapshotId);
    if (seeds.isEmpty()) {
      throw new ExternalSourceException("Wikidata returned no candidates for " + category);
    }
    return new WikidataSeedSnapshot(snapshotId, seeds);
  }

  public void completeSnapshot(MegaprojectCategory category, UUID snapshotId) {
    discoveryStateRepository.complete(snapshotId, category);
  }

  private Optional<WikidataDiscoverySeed> parseSeed(JsonNode binding) {
    String wikidataId = entityId(value(binding, "item"));
    int sitelinks = valueAsInt(binding);
    if (isInvalidWikidataId(wikidataId) || sitelinks < properties.minimumSitelinks()) {
      return Optional.empty();
    }
    return Optional.of(new WikidataDiscoverySeed(wikidataId, sitelinks));
  }

  private String seedQuery(String rootId, String cursor) {
    return """
                SELECT DISTINCT ?item ?sitelinks WHERE {
                  ?item wdt:P31/wdt:P279* wd:%s;
                        wdt:P18 ?image;
                        wikibase:sitelinks ?sitelinks.
                  FILTER(?sitelinks >= %d)
                  FILTER(STR(?item) > "%s")
                }
                ORDER BY STR(?item)
                LIMIT %d
                """
        .formatted(rootId, properties.minimumSitelinks(), cursor, properties.queryPageSize());
  }

  private Map<String, List<JsonNode>> fetchProjectFacts(Set<String> projectIds) {
    if (projectIds.isEmpty()) {
      return Map.of();
    }
    JsonNode bindings =
        executeSparql(projectFactsQuery(projectIds), "Wikidata targeted project facts")
            .path("results")
            .path("bindings");
    if (!bindings.isArray()) {
      throw new ExternalSourceException("Wikidata returned no targeted project facts");
    }
    return StreamSupport.stream(bindings.spliterator(), false)
        .filter(binding -> !isInvalidWikidataId(entityId(value(binding, "item"))))
        .collect(
            Collectors.groupingBy(
                binding -> entityId(value(binding, "item")),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private String projectFactsQuery(Set<String> projectIds) {
    String values =
        projectIds.stream().sorted().map(id -> "wd:" + id).collect(Collectors.joining(" "));
    return """
                SELECT DISTINCT ?item ?country ?countryCode ?image ?inception ?opened
                                ?costProperty WHERE {
                  VALUES ?item { %s }
                  {
                    ?item wdt:P17 ?country.
                    OPTIONAL { ?country wdt:P297 ?countryCode. }
                  }
                  UNION
                  {
                    FILTER NOT EXISTS { ?item wdt:P17 ?directCountry. }
                    ?item wdt:P131/wdt:P17 ?country.
                    OPTIONAL { ?country wdt:P297 ?countryCode. }
                  }
                  UNION { ?item wdt:P18 ?image. }
                  UNION { ?item wdt:P571 ?inception. }
                  UNION { ?item wdt:P1619 ?opened. }
                  UNION {
                    VALUES ?costProperty { wdt:P2130 wdt:P2769 }
                    ?item ?costProperty ?costValue.
                  }
                }
                ORDER BY ?item ?country ?image ?inception ?opened ?costProperty
                """
        .formatted(values);
  }

  private Map<String, List<WikidataRawMetric>> fetchProjectMetrics(Set<String> projectIds) {
    if (projectIds.isEmpty()) {
      return Map.of();
    }
    JsonNode bindings =
        executeSparql(projectMetricsQuery(projectIds), "Wikidata normalized project metrics")
            .path("results")
            .path("bindings");
    if (!bindings.isArray()) {
      throw new ExternalSourceException("Wikidata returned no normalized project metrics");
    }
    Map<String, List<WikidataRawMetric>> result = new LinkedHashMap<>();
    StreamSupport.stream(bindings.spliterator(), false)
        .map(this::parseProjectMetric)
        .flatMap(Optional::stream)
        .forEach(
            metric ->
                result
                    .computeIfAbsent(metric.projectId(), ignored -> new ArrayList<>())
                    .add(metric.metric()));
    result.replaceAll((ignored, metrics) -> selectPrimaryMetrics(metrics));
    return result;
  }

  private String projectMetricsQuery(Set<String> projectIds) {
    String values =
        projectIds.stream().sorted().map(id -> "wd:" + id).collect(Collectors.joining(" "));
    return """
                SELECT ?item ?metric ?sourceValue ?sourceUnit ?sourceUnitLabel
                       ?normalizedValue ?normalizedUnit ?rank WHERE {
                  VALUES ?item { %s }
                  VALUES (?property ?sourcePredicate ?normalizedPredicate ?metric) {
                    (p:P2043 psv:P2043 psn:P2043 "LENGTH")
                    (p:P2787 psv:P2787 psn:P2787 "MAIN_SPAN")
                    (p:P2048 psv:P2048 psn:P2048 "HEIGHT")
                    (p:P2046 psv:P2046 psn:P2046 "FLOOR_AREA")
                    (p:P1083 psv:P1083 psn:P1083 "CAPACITY")
                    (p:P2109 psv:P2109 psn:P2109 "POWER")
                    (p:P3872 psv:P3872 psn:P3872 "ANNUAL_PASSENGERS")
                    (p:P2234 psv:P2234 psn:P2234 "STORAGE_VOLUME")
                    (p:P2067 psv:P2067 psn:P2067 "MASS")
                  }
                  ?item ?property ?statement.
                  ?statement ?sourcePredicate ?sourceNode;
                             ?normalizedPredicate ?normalizedNode;
                             wikibase:rank ?rank.
                  ?sourceNode wikibase:quantityAmount ?sourceValue;
                              wikibase:quantityUnit ?sourceUnit.
                  ?normalizedNode wikibase:quantityAmount ?normalizedValue;
                                  wikibase:quantityUnit ?normalizedUnit.
                  OPTIONAL {
                    ?sourceUnit rdfs:label ?sourceUnitLabel.
                    FILTER(LANG(?sourceUnitLabel) = "en")
                  }
                  FILTER(?rank != wikibase:DeprecatedRank)
                }
                ORDER BY ?item ?metric DESC(?rank) DESC(?normalizedValue)
                """
        .formatted(values);
  }

  private Optional<WikidataProjectMetricBinding> parseProjectMetric(JsonNode binding) {
    String projectId = entityId(value(binding, "item"));
    try {
      ProjectMetricType type = ProjectMetricType.valueOf(value(binding, "metric"));
      BigDecimal sourceValue = new BigDecimal(value(binding, "sourceValue"));
      BigDecimal normalizedValue = new BigDecimal(value(binding, "normalizedValue"));
      String normalizedUnitId = entityId(value(binding, "normalizedUnit"));
      Optional<BigDecimal> canonicalValue =
          canonicalMetricValue(type, normalizedValue, normalizedUnitId);
      if (isInvalidWikidataId(projectId) || sourceValue.signum() <= 0 || canonicalValue.isEmpty()) {
        return Optional.empty();
      }
      String sourceUrl = "https://www.wikidata.org/wiki/" + projectId;
      String sourceUnitId = entityId(value(binding, "sourceUnit"));
      String sourceUnitLabel = value(binding, "sourceUnitLabel");
      String rank =
          value(binding, "rank").endsWith("PreferredRank") ? "PreferredRank" : "NormalRank";
      WikidataRawMetric metric =
          new WikidataRawMetric(
              type,
              canonicalValue.orElseThrow(),
              type.normalizedUnit(),
              sourceValue,
              WikidataMetricUnitFormatter.sourceUnit(
                  type, sourceUnitId, sourceUnitLabel, normalizedUnitId),
              rank,
              "Wikidata " + type.name().toLowerCase(Locale.ROOT) + " statement",
              sourceUrl,
              sourceUrl,
              wikidataMetricProperty(type),
              WikidataMetricUnitFormatter.evidenceText(
                  type, sourceValue, sourceUnitId, sourceUnitLabel, normalizedUnitId),
              false);
      return Optional.of(new WikidataProjectMetricBinding(projectId, metric));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private Optional<BigDecimal> canonicalMetricValue(
      ProjectMetricType type, BigDecimal value, String unitId) {
    BigDecimal result =
        switch (type) {
          case LENGTH, MAIN_SPAN, HEIGHT -> "Q11573".equals(unitId) ? value : null;
          case FLOOR_AREA -> "Q25343".equals(unitId) ? value : null;
          case CAPACITY, ANNUAL_PASSENGERS, ANNUAL_CARGO_TONNES, CONTAINER_CAPACITY -> value;
          case POWER -> "Q25236".equals(unitId) ? value.movePointLeft(6) : null;
          case STORAGE_VOLUME -> "Q25517".equals(unitId) ? value : null;
          case MASS -> "Q11570".equals(unitId) ? value : null;
        };
    return result == null || result.signum() <= 0
        ? Optional.empty()
        : Optional.of(result.setScale(6, RoundingMode.HALF_UP));
  }

  private String wikidataMetricProperty(ProjectMetricType type) {
    return switch (type) {
      case LENGTH -> "P2043";
      case MAIN_SPAN -> "P2787";
      case HEIGHT -> "P2048";
      case FLOOR_AREA -> "P2046";
      case CAPACITY -> "P1083";
      case POWER -> "P2109";
      case ANNUAL_PASSENGERS -> "P3872";
      case ANNUAL_CARGO_TONNES -> "MANUAL_CARGO_TONNES";
      case CONTAINER_CAPACITY -> "MANUAL_CONTAINER_CAPACITY";
      case STORAGE_VOLUME -> "P2234";
      case MASS -> "P2067";
    };
  }

  private List<WikidataRawMetric> selectPrimaryMetrics(List<WikidataRawMetric> metrics) {
    Map<ProjectMetricType, WikidataRawMetric> selected = new LinkedHashMap<>();
    metrics.forEach(metric -> selected.merge(metric.metricType(), metric, this::preferredMetric));
    return selected.values().stream().map(this::asPrimaryMetric).toList();
  }

  private WikidataRawMetric preferredMetric(WikidataRawMetric first, WikidataRawMetric second) {
    boolean firstPreferred = "PreferredRank".equals(first.statementRank());
    boolean secondPreferred = "PreferredRank".equals(second.statementRank());
    if (firstPreferred != secondPreferred) {
      return secondPreferred ? second : first;
    }
    return second.normalizedValue().compareTo(first.normalizedValue()) > 0 ? second : first;
  }

  private WikidataRawMetric asPrimaryMetric(WikidataRawMetric metric) {
    return new WikidataRawMetric(
        metric.metricType(),
        metric.normalizedValue(),
        metric.normalizedUnit(),
        metric.sourceValue(),
        metric.sourceUnit(),
        metric.statementRank(),
        metric.sourceName(),
        metric.sourceUrl(),
        metric.referenceUrl(),
        metric.sourceKey(),
        metric.evidenceText(),
        true);
  }

  private Optional<WikidataRejectionReason> rawProjectFailure(
      WikidataDiscoverySeed seed,
      Map<String, List<JsonNode>> factsByProject,
      Map<String, JsonNode> metadataEntities) {
    JsonNode project = metadataEntities.get(seed.wikidataId());
    List<JsonNode> facts = factsByProject.getOrDefault(seed.wikidataId(), List.of());
    if (project == null || project.path("missing").asBoolean(false)) {
      return Optional.of(WikidataRejectionReason.MISSING_ENTITY);
    }
    if (facts.isEmpty()) {
      return Optional.of(WikidataRejectionReason.MISSING_TARGETED_FACTS);
    }
    boolean hasCountry =
        facts.stream()
            .anyMatch(
                binding ->
                    !isInvalidWikidataId(entityId(value(binding, "country")))
                        && value(binding, "countryCode")
                            .toUpperCase(Locale.ROOT)
                            .matches("^[A-Z]{2}$"));
    if (!hasCountry) {
      return Optional.of(WikidataRejectionReason.MISSING_COUNTRY);
    }
    if (facts.stream()
        .map(binding -> imageFileTitle(value(binding, "image")))
        .allMatch(String::isBlank)) {
      return Optional.of(WikidataRejectionReason.MISSING_IMAGE_CLAIM);
    }
    if (englishWikipediaTitle(project).isBlank()) {
      return Optional.of(WikidataRejectionReason.MISSING_ENGLISH_WIKIPEDIA);
    }
    return Optional.empty();
  }

  private Optional<WikidataRawProject> parseRawProject(
      WikidataDiscoverySeed seed,
      Map<String, List<JsonNode>> factsByProject,
      Map<String, List<WikidataRawCost>> costsByProject,
      Map<String, List<WikidataRawMetric>> metricsByProject,
      Map<String, JsonNode> metadataEntities) {
    JsonNode project = metadataEntities.get(seed.wikidataId());
    List<JsonNode> facts = factsByProject.getOrDefault(seed.wikidataId(), List.of());
    if (project == null || project.path("missing").asBoolean(false) || facts.isEmpty()) {
      return Optional.empty();
    }
    JsonNode countryFact =
        facts.stream()
            .filter(
                binding -> {
                  String countryId = entityId(value(binding, "country"));
                  String countryCode = value(binding, "countryCode").toUpperCase(Locale.ROOT);
                  return !isInvalidWikidataId(countryId) && countryCode.matches("^[A-Z]{2}$");
                })
            .findFirst()
            .orElse(null);
    String countryId = countryFact == null ? "" : entityId(value(countryFact, "country"));
    String countryCode =
        countryFact == null ? "" : value(countryFact, "countryCode").toUpperCase(Locale.ROOT);
    List<String> images =
        facts.stream()
            .map(binding -> imageFileTitle(value(binding, "image")))
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(5)
            .toList();
    if (countryId.isBlank()
        || !countryCode.matches("^[A-Z]{2}$")
        || images.isEmpty()
        || englishWikipediaTitle(project).isBlank()) {
      return Optional.empty();
    }
    List<WikidataRawCost> costs = costsByProject.getOrDefault(seed.wikidataId(), List.of());
    WikidataRawCost cost =
        costs.stream()
            .filter(value -> value.costKind().supportsHowMuch())
            .reduce(WikidataRawCost::prefer)
            .orElse(null);
    return Optional.of(
        new WikidataRawProject(
            seed.wikidataId(),
            countryId,
            countryCode,
            images,
            projectYear(facts),
            seed.sitelinkCount(),
            cost,
            costs,
            metricsByProject.getOrDefault(seed.wikidataId(), List.of()),
            milestonesFromFacts(seed.wikidataId(), facts),
            facts.stream().map(fact -> (JsonNode) fact.deepCopy()).toList()));
  }

  private List<WikidataRawMilestone> milestonesFromFacts(String wikidataId, List<JsonNode> facts) {
    String sourceUrl = "https://www.wikidata.org/wiki/" + wikidataId;
    return facts.stream()
        .map(binding -> value(binding, "opened"))
        .filter(value -> !value.isBlank())
        .map(this::year)
        .filter(Objects::nonNull)
        .distinct()
        .map(
            openedYear ->
                new WikidataRawMilestone(
                    ProjectMilestoneType.OPENING,
                    null,
                    openedYear,
                    ProjectDatePrecision.YEAR,
                    "Wikidata date of official opening (P1619)",
                    sourceUrl,
                    sourceUrl,
                    "P1619",
                    openedYear.toString(),
                    true))
        .toList();
  }

  private Integer projectYear(List<JsonNode> facts) {
    Optional<Integer> inception =
        facts.stream()
            .map(binding -> year(value(binding, "inception")))
            .filter(Objects::nonNull)
            .min(Integer::compareTo);
    return inception.orElseGet(
        () ->
            facts.stream()
                .map(binding -> year(value(binding, "opened")))
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null));
  }

  private String imageFileTitle(String imageUrl) {
    HttpUrl url = HttpUrl.parse(imageUrl);
    List<String> pathSegments = url == null ? List.of() : url.pathSegments();
    if (url == null
        || !"commons.wikimedia.org".equalsIgnoreCase(url.host())
        || pathSegments.size() < 3
        || !"wiki".equals(pathSegments.get(0))
        || !"Special:FilePath".equals(pathSegments.get(1))) {
      return "";
    }
    return String.join("/", pathSegments.subList(2, pathSegments.size())).replace('_', ' ').trim();
  }

  private Map<String, List<WikidataRawCost>> fetchStructuredCosts(Set<String> projectIds) {
    if (projectIds.isEmpty()) {
      return Map.of();
    }
    JsonNode bindings =
        executeSparql(structuredCostsQuery(projectIds), "Wikidata project cost claims")
            .path("results")
            .path("bindings");
    if (!bindings.isArray()) {
      throw new ExternalSourceException("Wikidata returned no project cost claims");
    }
    Map<String, List<WikidataRawCost>> result = new LinkedHashMap<>();
    StreamSupport.stream(bindings.spliterator(), false)
        .map(this::parseStructuredCost)
        .flatMap(Optional::stream)
        .forEach(
            cost ->
                result
                    .computeIfAbsent(cost.projectId(), ignored -> new ArrayList<>())
                    .add(cost.cost()));
    result.replaceAll((ignored, costs) -> costs.stream().distinct().toList());
    return result;
  }

  private String structuredCostsQuery(Set<String> projectIds) {
    String values =
        projectIds.stream().sorted().map(id -> "wd:" + id).collect(Collectors.joining(" "));
    return """
                SELECT DISTINCT ?item ?costKind ?amount ?currencyCode ?rank
                                ?costYear ?referenceUrl ?scope ?scopeLabel WHERE {
                  VALUES ?item { %s }
                  VALUES (?statementProperty ?valueProperty ?costKind) {
                    (p:P2130 psv:P2130 "REPORTED_COST")
                    (p:P2769 psv:P2769 "APPROVED_BUDGET")
                  }
                  ?item ?statementProperty ?statement.
                  ?statement ?valueProperty ?valueNode;
                             wikibase:rank ?rank.
                  ?valueNode wikibase:quantityAmount ?amount;
                             wikibase:quantityUnit ?currency.
                  ?currency wdt:P498 ?currencyCode.
                  OPTIONAL { ?statement pq:P585 ?costYear. }
                  OPTIONAL {
                    ?statement pq:P518 ?scope.
                    OPTIONAL {
                      ?scope rdfs:label ?scopeLabel.
                      FILTER(LANG(?scopeLabel) = "en")
                    }
                  }
                  OPTIONAL {
                    ?statement prov:wasDerivedFrom ?reference.
                    ?reference pr:P854 ?referenceUrl.
                  }
                  FILTER(?rank != wikibase:DeprecatedRank)
                }
                ORDER BY ?item ?costKind DESC(?rank) DESC(?costYear) ?amount
                """
        .formatted(values);
  }

  private Optional<WikidataProjectCostBinding> parseStructuredCost(JsonNode binding) {
    String projectId = entityId(value(binding, "item"));
    try {
      ProjectCostKind costKind = ProjectCostKind.valueOf(value(binding, "costKind"));
      BigDecimal amount = new BigDecimal(value(binding, "amount"));
      String currency = value(binding, "currencyCode").toUpperCase(Locale.ROOT);
      if (isInvalidWikidataId(projectId)
          || amount.signum() <= 0
          || !currency.matches("^[A-Z]{3}$")) {
        return Optional.empty();
      }
      String rank =
          value(binding, "rank").endsWith("PreferredRank") ? "PreferredRank" : "NormalRank";
      String sourceKey = costKind == ProjectCostKind.REPORTED_COST ? "P2130" : "P2769";
      String sourceName =
          costKind == ProjectCostKind.REPORTED_COST
              ? "Wikidata capital cost (P2130)"
              : "Wikidata budget (P2769)";
      String sourceUrl = "https://www.wikidata.org/wiki/" + projectId;
      String scopeId = entityId(value(binding, "scope"));
      String scopeLabel = value(binding, "scopeLabel");
      return Optional.of(
          new WikidataProjectCostBinding(
              projectId,
              new WikidataRawCost(
                  costKind,
                  amount,
                  amount,
                  currency,
                  year(value(binding, "costYear")),
                  rank,
                  httpReferenceUrl(value(binding, "referenceUrl")),
                  sourceName,
                  sourceUrl,
                   sourceKey,
                   isInvalidWikidataId(scopeId)
                       ? "Whole project unless the source statement specifies otherwise"
                       : projectPartScopeNote(scopeLabel),
                   amount.toPlainString() + " " + currency)));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private String projectPartScopeNote(String scopeLabel) {
    return scopeLabel.isBlank()
        ? "Applies to a specific project part identified in the Wikidata statement"
        : "Applies to project part: " + scopeLabel;
  }

  private List<WikidataRawProject> enrichWikipediaData(
      List<WikidataRawProject> projects, Map<String, JsonNode> entities) {
    List<WikidataRawProject> leadEnriched = enrichWikipediaLeadData(projects, entities);
    List<WikidataRawProject> englishArticleEnriched =
        enrichWikipediaArticleCosts(leadEnriched, entities);
    return enrichLocalizedWikipediaArticleCosts(englishArticleEnriched, entities);
  }

  private List<WikidataRawProject> enrichWikipediaLeadData(
      List<WikidataRawProject> projects, Map<String, JsonNode> entities) {
    Map<String, WikidataRawProject> projectsByTitle = new LinkedHashMap<>();
    projects.forEach(
        project -> {
          String title = englishWikipediaTitle(entities.get(project.wikidataId()));
          if (!title.isBlank()) {
            projectsByTitle.put(title, project);
          }
        });
    if (projectsByTitle.isEmpty()) {
      return projects;
    }
    Map<String, WikipediaArticleSnapshot> snapshots =
        fetchWikipediaArticles("en", new ArrayList<>(projectsByTitle.keySet()), true);
    Map<String, WikidataRawProject> enrichedById = new LinkedHashMap<>();
    projectsByTitle.forEach(
        (title, project) -> {
          WikipediaArticleSnapshot snapshot = snapshots.get(title);
          if (snapshot == null) {
            return;
          }
          String snapshotUrl = wikipediaRevisionUrl(snapshot);
          WikipediaInfoboxData data =
              infoboxSupplementExtractor.extract(
                  snapshot.wikitext(), project.countryCode(), snapshotUrl, infoboxCostExtractor);
          List<WikidataRawCost> costs =
              data.costs().stream().map(match -> wikipediaCost(snapshot, match)).toList();
          List<WikidataRawMetric> metrics =
              data.metrics().stream().map(match -> wikipediaMetric(snapshot, match)).toList();
          List<WikidataRawMilestone> milestones =
              data.milestones().stream().map(match -> wikipediaMilestone(snapshot, match)).toList();
          enrichedById.put(
              project.wikidataId(), project.withSupplementalData(costs, metrics, milestones));
        });
    return projects.stream()
        .map(project -> enrichedById.getOrDefault(project.wikidataId(), project))
        .toList();
  }

  private List<WikidataRawProject> enrichLocalizedWikipediaArticleCosts(
      List<WikidataRawProject> projects, Map<String, JsonNode> entities) {
    List<WikidataRawProject> enriched = projects;
    for (String language : properties.wikipediaFallbackLanguages()) {
      Set<String> eligibleProjectIds =
          enriched.stream()
              .filter(project -> project.cost() == null)
              .filter(
                  project ->
                      language.equals(
                          preferredFallbackLanguage(
                              project.countryCode(), entities.get(project.wikidataId()))))
              .map(WikidataRawProject::wikidataId)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      if (!eligibleProjectIds.isEmpty()) {
        enriched = enrichWikipediaArticleCosts(enriched, entities, language, eligibleProjectIds);
      }
    }
    return enriched;
  }

  private List<WikidataRawProject> enrichWikipediaArticleCosts(
      List<WikidataRawProject> projects, Map<String, JsonNode> entities) {
    Set<String> eligibleProjectIds =
        projects.stream()
            .filter(project -> project.cost() == null)
            .map(WikidataRawProject::wikidataId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return enrichWikipediaArticleCosts(projects, entities, "en", eligibleProjectIds);
  }

  private List<WikidataRawProject> enrichWikipediaArticleCosts(
      List<WikidataRawProject> projects,
      Map<String, JsonNode> entities,
      String language,
      Set<String> eligibleProjectIds) {
    Map<String, WikidataRawProject> projectsByTitle = new LinkedHashMap<>();
    projects.stream()
        .filter(project -> eligibleProjectIds.contains(project.wikidataId()))
        .forEach(
            project -> {
              String title = wikipediaTitle(entities.get(project.wikidataId()), language);
              if (!title.isBlank()) {
                projectsByTitle.put(title, project);
              }
            });
    if (projectsByTitle.isEmpty()) {
      return projects;
    }
    Map<String, WikipediaArticleSnapshot> snapshots =
        fetchWikipediaArticles(language, new ArrayList<>(projectsByTitle.keySet()), false);
    Map<String, WikidataRawProject> enrichedById = new LinkedHashMap<>();
    projectsByTitle.forEach(
        (title, project) -> {
          WikipediaArticleSnapshot snapshot = snapshots.get(title);
          if (snapshot == null) {
            return;
          }
          List<WikidataRawCost> costs =
              infoboxCostExtractor
                  .extractCitedArticle(
                      snapshot.wikitext(),
                      language,
                      project.countryCode(),
                      wikipediaRevisionUrl(snapshot))
                  .stream()
                  .map(match -> wikipediaCost(snapshot, match))
                  .toList();
          if (!costs.isEmpty()) {
            enrichedById.put(
                project.wikidataId(), project.withSupplementalData(costs, List.of(), List.of()));
          }
        });
    return projects.stream()
        .map(project -> enrichedById.getOrDefault(project.wikidataId(), project))
        .toList();
  }

  private WikidataRawCost wikipediaCost(
      WikipediaArticleSnapshot snapshot, WikipediaCostMatch match) {
    String sourceUrl = wikipediaRevisionUrl(snapshot);
    boolean leadEvidence = match.fieldName().startsWith("article_lead_");
    boolean articleEvidence = match.fieldName().startsWith("article_body_");
    String languageSuffix = " [" + snapshot.language() + "]";
    return new WikidataRawCost(
        match.costKind(),
        match.amountLow(),
        match.amountHigh(),
        match.currency(),
        match.costYear(),
        "NormalRank",
        match.referenceUrl(),
        articleEvidence
            ? "Wikipedia article (cited)" + languageSuffix
            : leadEvidence
                ? "Wikipedia article lead"
                : match.referenceUrl().equals(sourceUrl)
                    ? "Wikipedia infobox snapshot"
                    : "Wikipedia infobox (cited)",
        sourceUrl,
        articleEvidence
            ? "WIKIPEDIA_ARTICLE:" + snapshot.language() + ":" + match.fieldName()
            : (leadEvidence ? "WIKIPEDIA_LEAD:" : "WIKIPEDIA_INFOBOX:") + match.fieldName(),
        match.scopeNote(),
        match.evidenceText());
  }

  private WikidataRawMetric wikipediaMetric(
      WikipediaArticleSnapshot snapshot, WikipediaMetricMatch match) {
    String sourceUrl = wikipediaRevisionUrl(snapshot);
    return new WikidataRawMetric(
        match.metricType(),
        match.normalizedValue(),
        match.normalizedUnit(),
        match.sourceValue(),
        match.sourceUnit(),
        "NormalRank",
        "Wikipedia infobox " + match.fieldName(),
        sourceUrl,
        match.referenceUrl(),
        "WIKIPEDIA_INFOBOX:" + match.fieldName(),
        match.evidenceText(),
        false);
  }

  private WikidataRawMilestone wikipediaMilestone(
      WikipediaArticleSnapshot snapshot, WikipediaMilestoneMatch match) {
    String sourceUrl = wikipediaRevisionUrl(snapshot);
    return new WikidataRawMilestone(
        match.milestoneType(),
        match.milestoneDate(),
        match.milestoneYear(),
        match.datePrecision(),
        "Wikipedia infobox " + match.fieldName(),
        sourceUrl,
        match.referenceUrl(),
        "WIKIPEDIA_INFOBOX:" + match.fieldName(),
        match.evidenceText(),
        false);
  }

  private String wikipediaRevisionUrl(WikipediaArticleSnapshot snapshot) {
    return wikipediaRevisionUrl(
        snapshot.language(), snapshot.resolvedTitle(), snapshot.revisionId());
  }

  private String wikipediaRevisionUrl(String language, String resolvedTitle, long revisionId) {
    return "https://"
        + language
        + ".wikipedia.org/w/index.php?title="
        + URLEncoder.encode(resolvedTitle, StandardCharsets.UTF_8)
        + "&oldid="
        + revisionId;
  }

  private Map<String, WikipediaArticleSnapshot> fetchWikipediaArticles(
      String language, List<String> titles, boolean leadOnly) {
    Map<String, WikipediaArticleSnapshot> snapshots = new LinkedHashMap<>();
    int batchSize =
        leadOnly
            ? properties.wikipediaArticleBatchSize()
            : Math.min(2, properties.wikipediaArticleBatchSize());
    for (int from = 0; from < titles.size(); from += batchSize) {
      int to = Math.min(from + batchSize, titles.size());
      List<String> batch = titles.subList(from, to);
      HttpUrl.Builder requestBuilder =
          trustedWikipediaApiEndpoint(language)
              .newBuilder()
              .addQueryParameter("action", "query")
              .addQueryParameter("prop", "revisions")
              .addQueryParameter("titles", String.join("|", batch))
              .addQueryParameter("rvprop", "ids|content")
              .addQueryParameter("rvslots", "main")
              .addQueryParameter("redirects", "1")
              .addQueryParameter("maxlag", String.valueOf(properties.maxLagSeconds()))
              .addQueryParameter("format", "json")
              .addQueryParameter("formatversion", "2");
      if (leadOnly) {
        requestBuilder.addQueryParameter("rvsection", "0");
      }
      JsonNode query =
          executeJson(
                  requestBuilder.build(),
                  "Wikipedia "
                      + language
                      + (leadOnly ? " lead" : " full article")
                      + " snapshot request")
              .path("query");
      Map<String, String> requestedByResolved = titleAliases(query, batch);
      JsonNode pages = query.path("pages");
      if (!pages.isArray()) {
        throw new ExternalSourceException("Wikipedia returned no article snapshots");
      }
      StreamSupport.stream(pages.spliterator(), false)
          .filter(page -> !page.path("missing").asBoolean(false))
          .forEach(
              page -> {
                String resolvedTitle = page.path("title").asText("");
                JsonNode revision = page.path("revisions").path(0);
                String wikitext = revision.path("slots").path("main").path("content").asText("");
                String requestedTitle =
                    requestedByResolved.getOrDefault(
                        normalizedWikipediaTitle(resolvedTitle), resolvedTitle);
                if (!wikitext.isBlank()) {
                  snapshots.put(
                      requestedTitle,
                      new WikipediaArticleSnapshot(
                          language,
                          requestedTitle,
                          resolvedTitle,
                          revision.path("revid").asLong(),
                          wikitext));
                }
              });
      if (to < titles.size()) {
        pauseBetweenRequests();
      }
    }
    return snapshots;
  }

  private WikidataCandidateEvaluation evaluateCandidate(
      MegaprojectCategory category,
      WikidataRawProject rawProject,
      Map<String, JsonNode> entities,
      Map<String, WikipediaPageSignals> wikipediaSignals,
      Map<String, WikimediaImageCandidate> reusableImages) {
    JsonNode projectEntity = entities.get(rawProject.wikidataId());
    String name = localizedValue(projectEntity, "labels");
    WikipediaPageSignals wikipedia =
        wikipediaSignals.getOrDefault(rawProject.wikidataId(), WikipediaPageSignals.empty());
    String description = localizedValue(projectEntity, "descriptions");
    boolean usesWikipediaDescription =
        contentQualityPolicy.isWeakDescription(description)
            && !contentQualityPolicy.isWeakDescription(wikipedia.description());
    if (usesWikipediaDescription) {
      description = wikipedia.description();
    }
    String wikipediaTitle = englishWikipediaTitle(projectEntity);
    String countryName = localizedValue(entities.get(rawProject.countryWikidataId()), "labels");
    if (countryName.isBlank()) {
      countryName = displayCountry(rawProject.countryCode());
    }
    CandidateRegion region = regionResolver.resolve(rawProject.countryCode());
    Optional<WikimediaImageCandidate> selectedImage =
        rawProject.imageFileTitles().stream()
            .map(this::normalizedFileTitle)
            .map(reusableImages::get)
            .filter(Objects::nonNull)
            .max(
                Comparator.comparingInt(this::imageQualityScore)
                    .thenComparing(WikimediaImageCandidate::sourceSha1));
    if (rawProject.cost() == null) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.MISSING_PLAYABLE_COST);
    }
    if (rawProject.projectYear() == null) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.MISSING_PROJECT_YEAR);
    }
    if (name.isBlank() || name.equals(rawProject.wikidataId())) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.MISSING_NAME);
    }
    if (description.isBlank()) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.MISSING_DESCRIPTION);
    }
    if (!categoryRelevancePolicy.isRelevant(
        category, name, description + " " + wikipedia.description())) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.CATEGORY_MISMATCH);
    }
    if (wikipediaTitle.isBlank()) {
      return WikidataCandidateEvaluation.rejected(
          WikidataRejectionReason.MISSING_ENGLISH_WIKIPEDIA);
    }
    if (countryName.isBlank() || countryName.equals(rawProject.countryCode())) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.MISSING_COUNTRY_NAME);
    }
    if (region == CandidateRegion.UNKNOWN) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.UNKNOWN_REGION);
    }
    if (selectedImage.isEmpty()) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.NO_LICENSED_IMAGE);
    }
    int pageviews = wikipedia.monthlyPageviews();
    int interestScore = interestScore(rawProject.sitelinkCount(), pageviews, description);
    WikidataCandidateImage image = toCandidateImage(selectedImage.orElseThrow());
    int qualityScore = contentQualityScore(interestScore, image.qualityScore());
    if (qualityScore < properties.minimumQualityScore()) {
      return WikidataCandidateEvaluation.rejected(WikidataRejectionReason.LOW_QUALITY);
    }
    List<WikidataProjectCost> costs = normalizeCosts(rawProject);
    Optional<WikidataProjectCost> cost =
        costs.stream().filter(WikidataProjectCost::answer).findFirst();
    if (cost.isEmpty()) {
      return WikidataCandidateEvaluation.rejected(costFailure(rawProject));
    }
    WikidataProjectCost selectedCost = cost.orElseThrow();
    List<WikidataRawMetric> publishedMetrics =
        selectPublishedMetrics(category, rawProject.metrics());
    List<WikidataRawMilestone> publishedMilestones =
        selectPublishedMilestones(rawProject.milestones());
    BudgetContentEvidence contentEvidence =
        new BudgetContentEvidence(
            true,
            publishedMetrics.stream().anyMatch(WikidataRawMetric::primary),
            publishedMilestones.stream().anyMatch(WikidataRawMilestone::primary));
    BudgetQualityAssessment qualityAssessment =
        contentQualityPolicy.assess(
            category, description, qualityScore, selectedCost, contentEvidence);
    ObjectNode sourcePayload = objectMapper.createObjectNode();
    ArrayNode sourceFacts = sourcePayload.putArray("wikidataFacts");
    rawProject.sourceFacts().forEach(sourceFacts::add);
    if (usesWikipediaDescription && !wikipedia.descriptionSourceUrl().isBlank()) {
      ObjectNode descriptionSource = sourcePayload.putObject("descriptionSource");
      descriptionSource.put("name", WIKIPEDIA_DESCRIPTION_SOURCE);
      descriptionSource.put("url", wikipedia.descriptionSourceUrl());
      descriptionSource.put("licenseLabel", WIKIPEDIA_LICENSE_LABEL);
      descriptionSource.put("licenseUrl", WIKIPEDIA_LICENSE_URL);
    }
    sourcePayload.put("wikipediaTitle", wikipediaTitle);
    sourcePayload.put("projectYear", rawProject.projectYear());
    sourcePayload.put("monthlyPageviews", pageviews);
    sourcePayload.put("interestScore", interestScore);
    sourcePayload.put("imageQualityScore", image.qualityScore());
    sourcePayload.put("qualityScore", qualityScore);
    sourcePayload.put("costSource", selectedCost.sourceKey());
    sourcePayload.put("costEvidence", selectedCost.evidenceText());
    sourcePayload.set("qualityAssessment", objectMapper.valueToTree(qualityAssessment));
    ObjectNode costSnapshot = sourcePayload.putObject("costSnapshot");
    costSnapshot.put("costKind", selectedCost.costKind().name());
    costSnapshot.put("sourceAmountLow", selectedCost.sourceAmountLow());
    costSnapshot.put("sourceAmountHigh", selectedCost.sourceAmountHigh());
    costSnapshot.put("sourceCurrency", selectedCost.sourceCurrency());
    if (selectedCost.costYear() != null) {
      costSnapshot.put("costYear", selectedCost.costYear());
    }
    if (!selectedCost.referenceUrl().isBlank()) {
      costSnapshot.put("referenceUrl", selectedCost.referenceUrl());
    }
    costSnapshot.put("sourceName", selectedCost.sourceName());
    costSnapshot.put("sourceUrl", selectedCost.sourceUrl());
    costSnapshot.put("sourceKey", selectedCost.sourceKey());
    sourcePayload.set("costClaims", objectMapper.valueToTree(costs));
    sourcePayload.set("metrics", objectMapper.valueToTree(publishedMetrics));
    sourcePayload.set("milestones", objectMapper.valueToTree(publishedMilestones));
    sourcePayload.set("selectedImage", objectMapper.valueToTree(image));
    return WikidataCandidateEvaluation.ready(
        new WikidataCandidate(
            rawProject.wikidataId(),
            name,
            description,
            rawProject.countryCode(),
            countryName,
            region,
            category,
            rawProject.projectYear(),
            BudgetDifficulty.HARD,
            image,
            selectedCost,
            "https://www.wikidata.org/wiki/" + rawProject.wikidataId(),
            sourcePayload.toString(),
            rawProject.sitelinkCount(),
            pageviews,
            interestScore,
            qualityScore,
            qualityAssessment,
            0));
  }

  private Map<String, WikimediaImageCandidate> fetchReusableImages(
      List<WikidataRawProject> rawProjects) {
    Set<String> imageTitles = new LinkedHashSet<>();
    rawProjects.stream()
        .filter(project -> project.cost() != null)
        .forEach(project -> imageTitles.addAll(project.imageFileTitles()));
    if (imageTitles.isEmpty()) {
      return Map.of();
    }
    try {
      Map<String, WikimediaImageCandidate> reusable = new LinkedHashMap<>();
      commonsClient
          .findExactImages(imageTitles)
          .forEach(image -> reusable.put(normalizedFileTitle(image.title()), image));
      return reusable;
    } catch (ExternalSourceException exception) {
      throw new ExternalSourceException("Wikimedia candidate image validation failed", exception);
    }
  }

  private Map<String, JsonNode> fetchEntities(
      Set<String> entityIds, String requestedProperties, boolean englishWikipediaOnly) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    List<String> ids = new ArrayList<>(entityIds);
    Map<String, JsonNode> entities = new LinkedHashMap<>();
    for (int from = 0; from < ids.size(); from += properties.entityBatchSize()) {
      int to = Math.min(from + properties.entityBatchSize(), ids.size());
      HttpUrl.Builder builder =
          trustedEntityApiEndpoint()
              .newBuilder()
              .addQueryParameter("action", "wbgetentities")
              .addQueryParameter("ids", String.join("|", ids.subList(from, to)))
              .addQueryParameter("props", requestedProperties)
              .addQueryParameter("languages", "en")
              .addQueryParameter("languagefallback", "1")
              .addQueryParameter("format", "json")
              .addQueryParameter("formatversion", "2")
              .addQueryParameter("maxlag", String.valueOf(properties.maxLagSeconds()));
      if (englishWikipediaOnly) {
        builder.addQueryParameter("sitefilter", "enwiki");
      }
      JsonNode responseEntities =
          executeJson(builder.build(), "Wikidata entity API request").path("entities");
      if (!responseEntities.isObject()) {
        throw new ExternalSourceException("Wikidata returned no entity metadata");
      }
      responseEntities
          .properties()
          .forEach(entry -> entities.put(entry.getKey(), entry.getValue()));
      if (to < ids.size()) {
        pauseBetweenRequests();
      }
    }
    return entities;
  }

  private Map<String, WikipediaPageSignals> fetchWikipediaSignals(
      List<WikidataRawProject> projects, Map<String, JsonNode> entities) {
    Map<String, String> wikipediaTitles = new LinkedHashMap<>();
    projects.stream()
        .filter(project -> project.cost() != null)
        .forEach(
            project -> {
              String title = englishWikipediaTitle(entities.get(project.wikidataId()));
              if (!title.isBlank()) {
                wikipediaTitles.put(project.wikidataId(), title);
              }
            });
    Map<String, WikipediaPageSignals> signalsByTitle =
        fetchWikipediaSignalsByTitle(
            new ArrayList<>(new LinkedHashSet<>(wikipediaTitles.values())));
    Map<String, WikipediaPageSignals> signalsById = new LinkedHashMap<>();
    wikipediaTitles.forEach(
        (wikidataId, title) ->
            signalsById.put(
                wikidataId, signalsByTitle.getOrDefault(title, WikipediaPageSignals.empty())));
    return signalsById;
  }

  private Map<String, WikipediaPageSignals> fetchWikipediaSignalsByTitle(List<String> titles) {
    Map<String, WikipediaPageSignals> signalsByTitle = new LinkedHashMap<>();
    for (int from = 0; from < titles.size(); from += properties.entityBatchSize()) {
      int to = Math.min(from + properties.entityBatchSize(), titles.size());
      List<String> batch = titles.subList(from, to);
      HttpUrl url =
          trustedWikipediaApiEndpoint()
              .newBuilder()
              .addQueryParameter("action", "query")
              .addQueryParameter("prop", "pageviews|extracts|revisions")
              .addQueryParameter("titles", String.join("|", batch))
              .addQueryParameter("pvipdays", String.valueOf(properties.pageviewDays()))
              .addQueryParameter("rvprop", "ids")
              .addQueryParameter("exintro", "1")
              .addQueryParameter("explaintext", "1")
              .addQueryParameter("exsentences", "2")
              .addQueryParameter("redirects", "1")
              .addQueryParameter("maxlag", String.valueOf(properties.maxLagSeconds()))
              .addQueryParameter("format", "json")
              .addQueryParameter("formatversion", "2")
              .build();
      JsonNode query = executeJson(url, "Wikipedia page signal request").path("query");
      JsonNode pages = query.path("pages");
      if (!pages.isArray()) {
        throw new ExternalSourceException("Wikipedia returned no page signal data");
      }
      Map<String, String> requestedByResolved = titleAliases(query, batch);
      StreamSupport.stream(pages.spliterator(), false)
          .filter(page -> !page.path("missing").asBoolean(false))
          .forEach(
              page -> {
                String resolvedTitle = page.path("title").asText("");
                long revisionId = page.path("revisions").path(0).path("revid").asLong(0);
                WikipediaPageSignals signals =
                    new WikipediaPageSignals(
                        sumPageviews(page.path("pageviews")),
                        page.path("extract").asText("").trim().replaceAll("\\s+", " "),
                        revisionId > 0
                            ? wikipediaRevisionUrl("en", resolvedTitle, revisionId)
                            : "");
                signalsByTitle.put(resolvedTitle, signals);
                String requestedTitle =
                    requestedByResolved.get(normalizedWikipediaTitle(resolvedTitle));
                if (requestedTitle != null) {
                  signalsByTitle.put(requestedTitle, signals);
                }
              });
      if (to < titles.size()) {
        pauseBetweenRequests();
      }
    }
    return signalsByTitle;
  }

  private Map<String, String> titleAliases(JsonNode query, List<String> requestedTitles) {
    Map<String, String> requestedByResolved = new LinkedHashMap<>();
    requestedTitles.forEach(
        title -> requestedByResolved.put(normalizedWikipediaTitle(title), title));
    applyTitleAliases(query.path("normalized"), requestedByResolved);
    applyTitleAliases(query.path("redirects"), requestedByResolved);
    return requestedByResolved;
  }

  private void applyTitleAliases(JsonNode mappings, Map<String, String> requestedByResolved) {
    if (!mappings.isArray()) {
      return;
    }
    StreamSupport.stream(mappings.spliterator(), false)
        .forEach(
            mapping -> {
              String from = normalizedWikipediaTitle(mapping.path("from").asText(""));
              String to = normalizedWikipediaTitle(mapping.path("to").asText(""));
              String requestedTitle = requestedByResolved.get(from);
              if (requestedTitle != null && !to.isBlank()) {
                requestedByResolved.put(to, requestedTitle);
              }
            });
  }

  private List<WikidataProjectCost> normalizeCosts(WikidataRawProject project) {
    List<WikidataProjectCost> normalized =
        project.costClaims().stream()
            .map(cost -> normalizeCost(cost, project.projectYear()))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    Optional<WikidataProjectCost> selected =
        normalized.stream()
            .filter(cost -> cost.costKind().supportsHowMuch())
            .max(
                Comparator.comparingInt(this::normalizedCostSourcePriority)
                    .thenComparingInt(cost -> cost.costKind().answerPriority())
                    .thenComparing(WikidataProjectCost::amountUsd));
    Map<ProjectCostKind, WikidataProjectCost> primaryByKind = new LinkedHashMap<>();
    normalized.forEach(
        cost -> primaryByKind.merge(cost.costKind(), cost, this::preferredCostClaim));
    return normalized.stream()
        .map(
            cost ->
                withSelection(
                    cost,
                    cost.equals(primaryByKind.get(cost.costKind())),
                    selected.map(cost::equals).orElse(false)))
        .toList();
  }

  private Optional<WikidataProjectCost> normalizeCost(
      WikidataRawCost rawCost, Integer projectYear) {
    Integer rateYear = rawCost.costYear() == null ? projectYear : rawCost.costYear();
    ExchangeRateQuote quote = exchangeRateQuote(rawCost.sourceCurrency(), rateYear);
    if (quote == null || quote.unitsPerUsd().signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal amountUsdLow =
        rawCost.sourceAmountLow().divide(quote.unitsPerUsd(), 2, RoundingMode.HALF_UP);
    BigDecimal amountUsdHigh =
        rawCost.sourceAmountHigh().divide(quote.unitsPerUsd(), 2, RoundingMode.HALF_UP);
    BigDecimal representative = midpoint(amountUsdLow, amountUsdHigh);
    if (representative.compareTo(properties.minimumProjectCostUsd()) < 0
        || representative.compareTo(properties.maximumProjectCostUsd()) > 0) {
      return Optional.empty();
    }
    return Optional.of(
        new WikidataProjectCost(
            rawCost.costKind(),
            rawCost.sourceAmountLow().setScale(2, RoundingMode.HALF_UP),
            rawCost.sourceAmountHigh().setScale(2, RoundingMode.HALF_UP),
            rawCost.sourceCurrency(),
            amountUsdLow,
            amountUsdHigh,
            rawCost.costYear(),
            quote.unitsPerUsd(),
            quote.rateDate(),
            quote.basis().name(),
            exchangeRateProvider(rawCost.sourceCurrency(), quote),
            rawCost.sourceName(),
            rawCost.sourceUrl(),
            rawCost.referenceUrl(),
            normalizationNote(rawCost, quote),
            rawCost.scopeNote(),
            rawCost.sourceKey(),
            rawCost.evidenceText(),
            ProjectCostVerificationStatus.SOURCE_BACKED,
            false,
            false));
  }

  private String exchangeRateProvider(String sourceCurrency, ExchangeRateQuote quote) {
    if ("USD".equals(sourceCurrency)) {
      return "NONE";
    }
    return quote.rateDate().getYear() < 1999 ? "BUNDESBANK_BBK" : "ECB";
  }

  private ExchangeRateQuote exchangeRateQuote(String currency, Integer rateYear) {
    if ("USD".equals(currency)) {
      LocalDate today = LocalDate.now();
      LocalDate rateDate =
          rateYear == null
              ? today
              : LocalDate.of(rateYear, 12, 31).isAfter(today)
                  ? today
                  : LocalDate.of(rateYear, 12, 31);
      return new ExchangeRateQuote(
          BigDecimal.ONE,
          rateDate,
          rateYear == null
              ? ExchangeRateBasis.LATEST_REFERENCE_RATE
              : ExchangeRateBasis.ANNUAL_AVERAGE);
    }
    if (rateYear != null && rateYear < 1948) {
      return null;
    }
    try {
      return exchangeRateClient.usdRatesForYear(rateYear).quotes().get(currency);
    } catch (ExternalSourceException exception) {
      log.debug(
          "Skipping {} cost because the {} exchange-rate snapshot is unavailable: {}",
          currency,
          rateYear == null ? "latest" : rateYear,
          conciseFailure(exception));
      return null;
    }
  }

  private WikidataProjectCost preferredCostClaim(
      WikidataProjectCost first, WikidataProjectCost second) {
    int firstPriority = normalizedCostSourcePriority(first);
    int secondPriority = normalizedCostSourcePriority(second);
    if (firstPriority != secondPriority) {
      return secondPriority > firstPriority ? second : first;
    }
    return second.amountUsd().compareTo(first.amountUsd()) > 0 ? second : first;
  }

  private WikidataProjectCost withSelection(
      WikidataProjectCost cost, boolean primary, boolean answer) {
    return new WikidataProjectCost(
        cost.costKind(),
        cost.sourceAmountLow(),
        cost.sourceAmountHigh(),
        cost.sourceCurrency(),
        cost.amountUsdLow(),
        cost.amountUsdHigh(),
        cost.costYear(),
        cost.unitsPerUsd(),
        cost.exchangeRateDate(),
        cost.exchangeRateBasis(),
        cost.exchangeRateProvider(),
        cost.sourceName(),
        cost.sourceUrl(),
        cost.referenceUrl(),
        cost.normalizationNote(),
        cost.scopeNote(),
        cost.sourceKey(),
        cost.evidenceText(),
        cost.verificationStatus(),
        primary,
        answer);
  }

  private int normalizedCostSourcePriority(WikidataProjectCost cost) {
    if (cost.sourceKey().startsWith("WIKIPEDIA_INFOBOX")) {
      return cost.sourceName().contains("(cited)") ? 4 : 2;
    }
    if (cost.sourceKey().startsWith("WIKIPEDIA_ARTICLE")) {
      return 3;
    }
    if (cost.sourceKey().startsWith("WIKIPEDIA_LEAD")) {
      return 1;
    }
    if ("P2130".equals(cost.sourceKey()) || "P2769".equals(cost.sourceKey())) {
      return "P2130".equals(cost.sourceKey()) ? 6 : 4;
    }
    return 0;
  }

  private WikidataRejectionReason costFailure(WikidataRawProject project) {
    boolean supportedCurrency =
        project.costClaims().stream()
            .filter(cost -> cost.costKind().supportsHowMuch())
            .anyMatch(cost -> "USD".equals(cost.sourceCurrency()));
    return supportedCurrency
        ? WikidataRejectionReason.COST_OUT_OF_RANGE
        : WikidataRejectionReason.UNSUPPORTED_CURRENCY;
  }

  private BigDecimal midpoint(BigDecimal low, BigDecimal high) {
    return low.add(high).multiply(HALF);
  }

  private List<WikidataRawMetric> selectPublishedMetrics(
      MegaprojectCategory category, List<WikidataRawMetric> metrics) {
    List<WikidataRawMetric> relevant =
        metrics.stream()
            .filter(metric -> isMetricRelevant(category, metric.metricType()))
            .distinct()
            .toList();
    Map<ProjectMetricType, WikidataRawMetric> selected = new LinkedHashMap<>();
    relevant.forEach(
        metric -> selected.merge(metric.metricType(), metric, this::preferredPublishedMetric));
    return relevant.stream()
        .map(
            metric -> withMetricSelection(metric, metric.equals(selected.get(metric.metricType()))))
        .toList();
  }

  private boolean isMetricRelevant(MegaprojectCategory category, ProjectMetricType metricType) {
    return switch (category) {
      case BRIDGE ->
          metricType == ProjectMetricType.LENGTH
              || metricType == ProjectMetricType.MAIN_SPAN
              || metricType == ProjectMetricType.HEIGHT;
      case METRO_RAIL, TUNNEL, ROAD_HIGHWAY -> metricType == ProjectMetricType.LENGTH;
      case STADIUM -> metricType == ProjectMetricType.CAPACITY;
      case SKYSCRAPER ->
          metricType == ProjectMetricType.HEIGHT || metricType == ProjectMetricType.FLOOR_AREA;
      case AIRPORT -> metricType == ProjectMetricType.ANNUAL_PASSENGERS;
      case DAM ->
          metricType == ProjectMetricType.HEIGHT
              || metricType == ProjectMetricType.LENGTH
              || metricType == ProjectMetricType.POWER
              || metricType == ProjectMetricType.STORAGE_VOLUME;
      case ENERGY -> metricType == ProjectMetricType.POWER;
      case SPACE -> metricType == ProjectMetricType.MASS || metricType == ProjectMetricType.POWER;
      case PORT ->
          metricType == ProjectMetricType.ANNUAL_CARGO_TONNES
              || metricType == ProjectMetricType.CONTAINER_CAPACITY;
    };
  }

  private WikidataRawMetric preferredPublishedMetric(
      WikidataRawMetric first, WikidataRawMetric second) {
    boolean firstWikidata = !first.sourceKey().startsWith("WIKIPEDIA_INFOBOX");
    boolean secondWikidata = !second.sourceKey().startsWith("WIKIPEDIA_INFOBOX");
    if (firstWikidata != secondWikidata) {
      return firstWikidata ? first : second;
    }
    return preferredMetric(first, second);
  }

  private List<WikidataRawMilestone> selectPublishedMilestones(
      List<WikidataRawMilestone> milestones) {
    List<WikidataRawMilestone> distinctMilestones = milestones.stream().distinct().toList();
    Map<ProjectMilestoneType, WikidataRawMilestone> selected = new LinkedHashMap<>();
    distinctMilestones.forEach(
        milestone ->
            selected.merge(milestone.milestoneType(), milestone, this::preferredMilestone));
    return distinctMilestones.stream()
        .map(
            milestone ->
                withMilestoneSelection(
                    milestone, milestone.equals(selected.get(milestone.milestoneType()))))
        .toList();
  }

  private WikidataRawMilestone preferredMilestone(
      WikidataRawMilestone first, WikidataRawMilestone second) {
    boolean firstWikipedia = first.sourceKey().startsWith("WIKIPEDIA_INFOBOX");
    boolean secondWikipedia = second.sourceKey().startsWith("WIKIPEDIA_INFOBOX");
    if (firstWikipedia != secondWikipedia) {
      return secondWikipedia ? second : first;
    }
    if (first.datePrecision() != second.datePrecision()) {
      return second.datePrecision() == ProjectDatePrecision.DAY ? second : first;
    }
    int firstYear = milestoneYear(first);
    int secondYear = milestoneYear(second);
    if (first.milestoneType() == ProjectMilestoneType.CONSTRUCTION_START) {
      return secondYear < firstYear ? second : first;
    }
    return secondYear > firstYear ? second : first;
  }

  private int milestoneYear(WikidataRawMilestone milestone) {
    return milestone.milestoneDate() == null
        ? milestone.milestoneYear()
        : milestone.milestoneDate().getYear();
  }

  private WikidataRawMilestone withMilestoneSelection(
      WikidataRawMilestone milestone, boolean primary) {
    return new WikidataRawMilestone(
        milestone.milestoneType(),
        milestone.milestoneDate(),
        milestone.milestoneYear(),
        milestone.datePrecision(),
        milestone.sourceName(),
        milestone.sourceUrl(),
        milestone.referenceUrl(),
        milestone.sourceKey(),
        milestone.evidenceText(),
        primary);
  }

  private WikidataRawMetric withMetricSelection(WikidataRawMetric metric, boolean primary) {
    return new WikidataRawMetric(
        metric.metricType(),
        metric.normalizedValue(),
        metric.normalizedUnit(),
        metric.sourceValue(),
        metric.sourceUnit(),
        metric.statementRank(),
        metric.sourceName(),
        metric.sourceUrl(),
        metric.referenceUrl(),
        metric.sourceKey(),
        metric.evidenceText(),
        primary);
  }

  private String normalizationNote(WikidataRawCost cost, ExchangeRateQuote quote) {
    String origin = cost.sourceName() + " amount";
    if ("USD".equals(cost.sourceCurrency())) {
      return origin + " reported in USD; no currency conversion applied.";
    }
    String rateDescription =
        quote.basis() == ExchangeRateBasis.ANNUAL_AVERAGE
            ? "annual average reference rate for " + quote.rateDate().getYear()
            : "reference rate for " + quote.rateDate();
    return origin
        + " converted from "
        + cost.sourceCurrency()
        + " to USD using the Frankfurter "
        + rateDescription
        + " ("
        + quote.unitsPerUsd().stripTrailingZeros().toPlainString()
        + " "
        + cost.sourceCurrency()
        + " per USD).";
  }

  private WikidataCandidateImage toCandidateImage(WikimediaImageCandidate candidate) {
    return new WikidataCandidateImage(
        candidate.pageId(),
        candidate.sourceSha1(),
        candidate.title(),
        candidate.thumbnailUrl(),
        candidate.originalUrl(),
        candidate.sourcePageUrl(),
        candidate.authorName(),
        candidate.credit(),
        candidate.licenseLabel(),
        candidate.licenseUrl(),
        candidate.description(),
        candidate.mimeType(),
        candidate.sourceBytes(),
        candidate.width(),
        candidate.height(),
        imageQualityScore(candidate),
        null);
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

  private int contentQualityScore(int interestScore, int imageQualityScore) {
    return Math.min(100, (int) Math.round(interestScore * 0.82 + imageQualityScore * 0.18));
  }

  private int interestScore(int sitelinkCount, int monthlyPageviews, String description) {
    int knowledgeGraphScore = Math.min(20, (int) Math.round(Math.log1p(sitelinkCount) * 4));
    int pageviewScore =
        monthlyPageviews == 0
            ? 0
            : Math.min(78, (int) Math.round(Math.log10(monthlyPageviews + 1L) * 16));
    return Math.min(100, knowledgeGraphScore + pageviewScore + (description.isBlank() ? 0 : 2));
  }

  private int sumPageviews(JsonNode dailyPageviews) {
    if (!dailyPageviews.isObject()) {
      return 0;
    }
    int total = 0;
    var values = dailyPageviews.elements();
    while (values.hasNext()) {
      JsonNode value = values.next();
      if (value.isIntegralNumber()) {
        total += value.asInt();
      }
    }
    return Math.max(0, total);
  }

  private HttpUrl trustedSparqlEndpoint() {
    return trustedEndpoint(properties.sparqlUrl(), "query.wikidata.org", "SPARQL");
  }

  private HttpUrl trustedEntityApiEndpoint() {
    return trustedEndpoint(properties.entityApiUrl(), "www.wikidata.org", "entity API");
  }

  private HttpUrl trustedWikipediaApiEndpoint() {
    return trustedEndpoint(properties.wikipediaApiUrl(), "en.wikipedia.org", "API");
  }

  private HttpUrl trustedWikipediaApiEndpoint(String language) {
    HttpUrl englishEndpoint = trustedWikipediaApiEndpoint();
    if ("en".equals(language)) {
      return englishEndpoint;
    }
    if (!properties.wikipediaFallbackLanguages().contains(language)) {
      throw new ExternalSourceException("Unsupported Wikipedia language endpoint");
    }
    return englishEndpoint.newBuilder().host(language + ".wikipedia.org").build();
  }

  private HttpUrl trustedEndpoint(String configuredUrl, String expectedHost, String sourceName) {
    HttpUrl endpoint = HttpUrl.parse(configuredUrl);
    if (endpoint == null
        || !endpoint.isHttps()
        || !expectedHost.equalsIgnoreCase(endpoint.host())) {
      throw new ExternalSourceException("Invalid Wikidata " + sourceName + " endpoint");
    }
    return endpoint;
  }

  private JsonNode executeJson(HttpUrl url, String operation) {
    Request request =
        new Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", httpProperties.userAgent())
            .get()
            .build();
    return executeJson(request, operation);
  }

  private JsonNode executeSparql(String query, String operation) {
    FormBody body = new FormBody.Builder().add("query", query).add("format", "json").build();
    Request request =
        new Request.Builder()
            .url(trustedSparqlEndpoint())
            .header("Accept", "application/sparql-results+json")
            .header("User-Agent", httpProperties.userAgent())
            .tag(RetryableRequestTag.class, RetryableRequestTag.READ_ONLY)
            .post(body)
            .build();
    return executeJson(request, operation);
  }

  private JsonNode executeJson(Request request, String operation) {
    try (Response response = externalSourceHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new ExternalSourceException(operation + " failed with HTTP " + response.code());
      }
      ResponseBody body = response.body();
      if (body == null) {
        throw new ExternalSourceException(operation + " returned an empty response");
      }
      JsonNode payload = objectMapper.readTree(body.charStream());
      JsonNode apiError = payload.path("error");
      if (!apiError.isMissingNode() && !apiError.isNull()) {
        throw mediaWikiApiError(response, apiError, operation);
      }
      return payload;
    } catch (IOException exception) {
      throw new ExternalSourceException(operation + " failed", exception);
    }
  }

  private ExternalSourceException mediaWikiApiError(
      Response response, JsonNode apiError, String operation) {
    String code = apiError.path("code").asText("unknown");
    String details = apiError.path("info").asText("No error details returned");
    if ("maxlag".equalsIgnoreCase(code)) {
      return new MediaWikiMaxLagException(operation, details, maxLagDelay(response, apiError));
    }
    return new ExternalSourceException(operation + " returned API error " + code + ": " + details);
  }

  private Duration maxLagDelay(Response response, JsonNode apiError) {
    long retryAfterSeconds = positiveLong(response.header("Retry-After"));
    long reportedLagSeconds = Math.max(0L, (long) Math.ceil(apiError.path("lag").asDouble(0)));
    return Duration.ofSeconds(Math.max(1L, Math.max(retryAfterSeconds, reportedLagSeconds)));
  }

  private long positiveLong(String value) {
    try {
      return Math.max(0L, Long.parseLong(Objects.requireNonNullElse(value, "0")));
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }

  private String entityId(String entityUrl) {
    if (entityUrl.startsWith(ENTITY_HTTP_PREFIX)) {
      return entityUrl.substring(ENTITY_HTTP_PREFIX.length());
    }
    if (entityUrl.startsWith(ENTITY_HTTPS_PREFIX)) {
      return entityUrl.substring(ENTITY_HTTPS_PREFIX.length());
    }
    return "";
  }

  private String normalizedFileTitle(String value) {
    String title = value == null ? "" : value.trim();
    if (title.regionMatches(true, 0, "File:", 0, 5)) {
      title = title.substring(5);
    }
    return title.replace('_', ' ').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String localizedValue(JsonNode entity, String property) {
    if (entity == null) {
      return "";
    }
    JsonNode localizedValues = entity.path(property);
    String english = localizedValues.path("en").path("value").asText("").trim();
    if (!english.isBlank()) {
      return english;
    }
    return StreamSupport.stream(
            ((Iterable<JsonNode>) localizedValues::elements).spliterator(), false)
        .map(value -> value.path("value").asText("").trim())
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("");
  }

  private String englishWikipediaTitle(JsonNode entity) {
    return wikipediaTitle(entity, "en");
  }

  private String wikipediaTitle(JsonNode entity, String language) {
    return entity == null
        ? ""
        : entity.path("sitelinks").path(language + "wiki").path("title").asText("").trim();
  }

  private String preferredFallbackLanguage(String countryCode, JsonNode entity) {
    String language = countryWikipediaLanguage(countryCode);
    return properties.wikipediaFallbackLanguages().contains(language)
            && !wikipediaTitle(entity, language).isBlank()
        ? language
        : "";
  }

  private String countryWikipediaLanguage(String countryCode) {
    return switch (countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT)) {
      case "DE", "AT", "CH", "LI" -> "de";
      case "FR",
              "BE",
              "LU",
              "MC",
              "CA",
              "DZ",
              "MA",
              "TN",
              "SN",
              "CI",
              "CM",
              "ML",
              "NE",
              "BF",
              "BJ",
              "TG",
              "CG",
              "CD",
              "GA",
              "GN",
              "MG",
              "HT" ->
          "fr";
      case "ES",
              "MX",
              "AR",
              "BO",
              "CL",
              "CO",
              "CR",
              "CU",
              "DO",
              "EC",
              "GQ",
              "GT",
              "HN",
              "NI",
              "PA",
              "PY",
              "PE",
              "PR",
              "SV",
              "UY",
              "VE" ->
          "es";
      case "PT", "BR", "AO", "MZ", "CV", "GW", "ST", "TL" -> "pt";
      case "RU", "BY", "KZ", "KG" -> "ru";
      case "IT", "SM", "VA" -> "it";
      case "NL", "SR" -> "nl";
      case "PL" -> "pl";
      case "TR" -> "tr";
      default -> "";
    };
  }

  private String normalizedWikipediaTitle(String value) {
    return value == null
        ? ""
        : value.trim().replace('_', ' ').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String displayCountry(String countryCode) {
    try {
      return new Locale.Builder()
          .setRegion(countryCode)
          .build()
          .getDisplayCountry(Locale.ENGLISH)
          .trim();
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private boolean isInvalidWikidataId(String value) {
    return value == null || !value.matches("^Q[1-9][0-9]*$");
  }

  private int valueAsInt(JsonNode binding) {
    try {
      return Integer.parseInt(value(binding, "sitelinks"));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private Integer year(String value) {
    if (value == null || !value.matches("^[+-]?\\d{4,}.*")) {
      return null;
    }
    try {
      int separator = value.indexOf('-', 1);
      String year = separator < 0 ? value : value.substring(0, separator);
      int parsed = Integer.parseInt(year.replace("+", ""));
      return parsed >= 1850 && parsed <= 2200 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String value(JsonNode binding, String name) {
    return binding.path(name).path("value").asText("").trim();
  }

  private String httpReferenceUrl(String value) {
    HttpUrl url = HttpUrl.parse(value);
    return url == null || !("http".equals(url.scheme()) || url.isHttps()) ? "" : url.toString();
  }

  private void pauseBetweenRequests() {
    if (!properties.requestDelay().isZero() && !Thread.currentThread().isInterrupted()) {
      LockSupport.parkNanos(properties.requestDelay().toNanos());
    }
  }

  private <T> T withBatchRecovery(Supplier<T> operation, String operationName) {
    ExternalSourceException lastFailure = null;
    for (int attempt = 1; attempt <= properties.batchMaxAttempts(); attempt++) {
      try {
        return operation.get();
      } catch (ExternalSourceException exception) {
        lastFailure = exception;
        if (attempt < properties.batchMaxAttempts()) {
          Duration recoveryDelay = batchRecoveryDelay(attempt, exception);
          log.warn(
              "{} failed on batch attempt {}/{}: {}; retrying in {} ms",
              operationName,
              attempt,
              properties.batchMaxAttempts(),
              conciseFailure(exception),
              recoveryDelay.toMillis());
          pause(recoveryDelay);
          if (Thread.currentThread().isInterrupted()) {
            throw new ExternalSourceException(
                operationName + " was interrupted during recovery", exception);
          }
        }
      }
    }
    throw new ExternalSourceException(
        operationName
            + " failed after "
            + properties.batchMaxAttempts()
            + " batch attempts: "
            + conciseFailure(lastFailure),
        lastFailure);
  }

  private Duration batchRecoveryDelay(int failedAttempt, ExternalSourceException failure) {
    long multiplier = 1L << Math.min(failedAttempt - 1, 4);
    Duration exponential =
        minDuration(
            properties.batchRecoveryDelay().multipliedBy(multiplier),
            properties.batchRecoveryMaxDelay());
    Duration serverSuggested =
        failure instanceof MediaWikiMaxLagException maxLag
            ? maxLag.suggestedDelay()
            : Duration.ZERO;
    Duration base = maxDuration(exponential, serverSuggested);
    long baseMillis = Math.max(1L, base.toMillis());
    long jitterCeiling = Math.max(1L, baseMillis / 4L);
    long jitterMillis = ThreadLocalRandom.current().nextLong(jitterCeiling + 1L);
    return minDuration(
        Duration.ofMillis(baseMillis + jitterMillis), properties.batchRecoveryMaxDelay());
  }

  private Duration minDuration(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  private Duration maxDuration(Duration first, Duration second) {
    return first.compareTo(second) >= 0 ? first : second;
  }

  private List<String> summarizedFailures(List<String> failures) {
    if (failures.size() <= 3) {
      return List.copyOf(failures);
    }
    List<String> summary = new ArrayList<>(failures.subList(0, 3));
    summary.add("and " + (failures.size() - 3) + " additional deferred sub-batches");
    return List.copyOf(summary);
  }

  private String conciseFailure(Throwable failure) {
    if (failure == null) {
      return "Unknown external source failure";
    }
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      message = failure.getMessage();
    }
    return message == null || message.isBlank()
        ? "Unknown external source failure"
        : message.replaceAll("\\s+", " ").trim();
  }

  private void pause(Duration duration) {
    if (!duration.isZero() && !Thread.currentThread().isInterrupted()) {
      LockSupport.parkNanos(duration.toNanos());
    }
  }
}
