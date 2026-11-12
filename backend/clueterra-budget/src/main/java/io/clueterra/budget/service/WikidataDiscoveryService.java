package io.clueterra.budget.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import io.clueterra.budget.config.BudgetSyncProperties;
import io.clueterra.budget.config.WikidataProperties;
import io.clueterra.budget.exception.ExternalSourceException;
import io.clueterra.budget.exception.SyncAlreadyRunningException;
import io.clueterra.budget.project.BudgetSyncResult;
import io.clueterra.budget.project.BudgetSyncRunRow;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.WikidataCandidate;
import io.clueterra.budget.repository.BudgetCandidateEnrichmentRepository;
import io.clueterra.budget.repository.BudgetSyncRunRepository;
import io.clueterra.budget.repository.WikidataCandidateRepository;
import io.clueterra.budget.source.wikidata.WikidataCandidateSelector;
import io.clueterra.budget.source.wikidata.WikidataCategoryDiscoveryResult;
import io.clueterra.budget.source.wikidata.WikidataSparqlClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WikidataDiscoveryService {

  private final WikidataSparqlClient client;
  private final WikidataProperties properties;
  private final BudgetSyncProperties syncProperties;
  private final WikidataCandidateSelector candidateSelector;
  private final WikidataCandidateRepository candidateRepository;
  private final BudgetCandidateEnrichmentRepository enrichmentRepository;
  private final BudgetSyncRunRepository syncRunRepository;

  public BudgetSyncResult synchronize(Consumer<String> progress, Runnable operationHeartbeat) {
    long runId =
        syncRunRepository
            .startRun(WikidataSparqlClient.SOURCE_CODE, syncProperties.staleRunTimeout())
            .orElseThrow(
                () ->
                    new SyncAlreadyRunningException("A Wikidata discovery run is already active"));
    int discovered = 0;
    int retained = 0;
    int categoriesAttempted = 0;
    int categoriesSucceeded = 0;
    List<String> failures = new ArrayList<>();
    MegaprojectCategory[] categories = MegaprojectCategory.values();
    try {
      for (int categoryIndex = 0; categoryIndex < categories.length; categoryIndex++) {
        categoriesAttempted++;
        heartbeat(runId, operationHeartbeat);
        MegaprojectCategory category = categories[categoryIndex];
        try {
          WikidataCategoryDiscoveryResult discovery =
              client.discover(
                  category,
                  () -> heartbeat(runId, operationHeartbeat),
                  progress,
                  this::stageIncrementalCandidates);
          discovered += discovery.discoveredCount();
          List<WikidataCandidate> candidates = candidateSelector.select(discovery.candidates());
          retained += candidates.size();
          report(
              progress, categoryProgress(category, discovery.discoveredCount(), candidates.size()));
          if (persistCategoryResult(category, discovery, candidates, failures, progress)) {
            categoriesSucceeded++;
          }
        } catch (ExternalSourceException exception) {
          String failure = "%s: %s".formatted(category, exception.getMessage());
          failures.add(failure);
          progress.accept("Skipped %s: %s".formatted(category, exception.getMessage()));
          log.warn("Wikidata category discovery failed: {}", failure, exception);
        }
        report(
            progress,
            discoveryProgress(categoryIndex + 1, categories.length, discovered, retained));
        heartbeat(runId, operationHeartbeat);
        pauseBetweenRequests();
      }
      int duplicatesRemoved = enrichmentRepository.deduplicateCandidates();
      progress.accept("Removed %d cross-category duplicates".formatted(duplicatesRemoved));
      if (failures.isEmpty() && categoriesSucceeded == categories.length) {
        int retired = candidateRepository.retireUnavailableProjects();
        progress.accept(
            "Retired %d projects missing from the complete source snapshot".formatted(retired));
      } else {
        progress.accept("Skipped project retirement because the Wikidata snapshot is incomplete");
      }
      int republished = candidateRepository.republishAvailable();
      progress.accept("Reused stored images for %d existing candidates".formatted(republished));
      BudgetSyncRunRow completed =
          completeRun(
              runId, categories.length, categoriesSucceeded, discovered, retained, failures);
      return new BudgetSyncResult(
          runId,
          WikidataSparqlClient.SOURCE_CODE,
          completed.status(),
          categories.length,
          categoriesSucceeded,
          failures.size(),
          discovered,
          retained,
          List.copyOf(failures),
          completed.completedAt());
    } catch (RuntimeException exception) {
      markFailed(runId, categoriesAttempted, categoriesSucceeded, discovered, retained, exception);
      throw exception;
    }
  }

  private boolean persistCategoryResult(
      MegaprojectCategory category,
      WikidataCategoryDiscoveryResult discovery,
      List<WikidataCandidate> candidates,
      List<String> failures,
      Consumer<String> progress) {
    if (discovery.complete()) {
      candidateRepository.finalizeCategorySnapshot(category, candidates);
      client.completeSnapshot(category, discovery.snapshotId());
      return true;
    }

    candidateRepository.upsertCandidates(candidates);
    String failure = incompleteCategoryMessage(category, discovery);
    failures.add(failure);
    progress.accept(
        "Deferred %s: %d cost-ready candidates staged, %d seeds remain"
            .formatted(category, candidates.size(), discovery.remainingCount()));
    log.warn("Wikidata category discovery is incomplete: {}", failure);
    return false;
  }

  private void report(Consumer<String> progress, String message) {
    progress.accept(message);
    log.info(message);
  }

  private void stageIncrementalCandidates(List<WikidataCandidate> candidates) {
    if (!candidates.isEmpty()) {
      candidateRepository.upsertCandidates(candidateSelector.select(candidates));
    }
  }

  private String incompleteCategoryMessage(
      MegaprojectCategory category, WikidataCategoryDiscoveryResult discovery) {
    String details =
        discovery.deferredFailures().isEmpty()
            ? "No external error detail was captured"
            : String.join("; ", discovery.deferredFailures());
    return "%s metadata remains incomplete: %d/%d seeds deferred; successful batches and candidates were persisted. %s"
        .formatted(category, discovery.remainingCount(), discovery.discoveredCount(), details);
  }

  private void pauseBetweenRequests() {
    if (!properties.requestDelay().isZero() && !Thread.currentThread().isInterrupted()) {
      LockSupport.parkNanos(properties.requestDelay().toNanos());
    }
  }

  private void heartbeat(long runId, Runnable operationHeartbeat) {
    syncRunRepository.heartbeat(runId);
    operationHeartbeat.run();
  }

  private String discoveryProgress(
      int categoriesProcessed, int categoryCount, int discovered, int retained) {
    int percent = (categoriesProcessed * 100) / categoryCount;
    return "Wikidata discovery: %d/%d categories (%d%%), %d/%d candidates retained"
        .formatted(categoriesProcessed, categoryCount, percent, retained, discovered);
  }

  private String categoryProgress(MegaprojectCategory category, int discovered, int retained) {
    int percent = discovered == 0 ? 0 : (retained * 100) / discovered;
    return "%s: retained %d/%d candidates (%d%%) after metadata, licensed-image and source-cost screening"
        .formatted(category, retained, discovered, percent);
  }

  private void markFailed(
      long runId,
      int categoriesAttempted,
      int categoriesSucceeded,
      int discovered,
      int retained,
      RuntimeException originalFailure) {
    try {
      syncRunRepository.finishFailure(
          runId,
          categoriesAttempted,
          categoriesSucceeded,
          discovered,
          retained,
          originalFailure.getMessage());
    } catch (RuntimeException persistenceFailure) {
      originalFailure.addSuppressed(persistenceFailure);
      log.error("Unable to mark Wikidata discovery run {} as failed", runId, persistenceFailure);
    }
  }

  private BudgetSyncRunRow completeRun(
      long runId,
      int categoriesAttempted,
      int categoriesSucceeded,
      int discovered,
      int retained,
      List<String> failures) {
    if (failures.isEmpty()) {
      return syncRunRepository.finishSuccess(
          runId, categoriesAttempted, categoriesSucceeded, discovered, retained);
    }
    return syncRunRepository.finishPartial(
        runId,
        categoriesAttempted,
        categoriesSucceeded,
        discovered,
        retained,
        String.join("; ", failures));
  }
}
