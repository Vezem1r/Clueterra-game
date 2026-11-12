package io.clueterra.budget.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.budget.config.BudgetEnrichmentProperties;
import io.clueterra.budget.project.BudgetCandidateImageFailure;
import io.clueterra.budget.project.BudgetCandidateImageWorkItem;
import io.clueterra.budget.project.BudgetCandidateStoredImage;
import io.clueterra.budget.project.BudgetEnrichmentBatchResult;
import io.clueterra.budget.repository.BudgetCandidateEnrichmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetCandidateEnrichmentService {

  private final BudgetEnrichmentProperties properties;
  private final BudgetCandidateEnrichmentRepository enrichmentRepository;
  private final WikidataCandidateImageService imageService;

  public BudgetEnrichmentBatchResult processImageBatch() {
    List<BudgetCandidateImageWorkItem> candidates =
        enrichmentRepository.claimImageBatch(
            properties.candidateImageBatchSize(), staleProcessingThreshold());
    if (candidates.isEmpty()) {
      return BudgetEnrichmentBatchResult.empty();
    }
    int succeeded = 0;
    int failed = 0;
    for (BudgetCandidateImageWorkItem candidate : candidates) {
      try {
        BudgetCandidateStoredImage image = imageService.downloadAndStore(candidate);
        enrichmentRepository.publishImage(candidate, image, LocalDate.now(ZoneOffset.UTC));
        succeeded++;
      } catch (RuntimeException exception) {
        failed++;
        String failure = conciseMessage(exception);
        enrichmentRepository.failImage(candidate, failure);
        log.warn(
            "Candidate image enrichment failed for {}/{}: {}",
            candidate.wikidataId(),
            candidate.category(),
            failure);
        log.debug("Candidate image enrichment failure details", exception);
      }
    }
    return new BudgetEnrichmentBatchResult(candidates.size(), succeeded, failed);
  }

  public int countPendingImageCandidates() {
    return enrichmentRepository.countPendingImageCandidates(staleProcessingThreshold());
  }

  public List<BudgetCandidateImageFailure> findFailedImageCandidates(int limit) {
    return enrichmentRepository.findFailedImageCandidates(limit);
  }

  public int resetImageFailures() {
    return enrichmentRepository.resetImageFailures();
  }

  private Instant staleProcessingThreshold() {
    return Instant.now().minus(properties.processingTimeout());
  }

  private String conciseMessage(RuntimeException exception) {
    Throwable cause = exception;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    return message.length() <= 900 ? message : message.substring(0, 897) + "...";
  }
}
