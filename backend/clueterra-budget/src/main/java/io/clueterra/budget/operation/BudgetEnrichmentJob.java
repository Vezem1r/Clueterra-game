package io.clueterra.budget.operation;

import java.util.List;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import io.clueterra.budget.project.BudgetCandidateImageFailure;
import io.clueterra.budget.project.BudgetEnrichmentBatchResult;
import io.clueterra.budget.service.BudgetCandidateEnrichmentService;
import io.clueterra.budget.service.console.BudgetConsoleLogWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetEnrichmentJob {

  private static final int MAX_LOGGED_IMAGE_FAILURES = 50;

  private final JobScheduler jobScheduler;
  private final BudgetCandidateEnrichmentService enrichmentService;
  private final BudgetConsoleLogWriter console;

  public void enqueueImagePipeline(String operationId) {
    BudgetStageProgress progress =
        BudgetStageProgress.start(enrichmentService.countPendingImageCandidates());
    stageInfo("Licensed candidate images: 0/" + progress.total() + " candidates");
    enqueueImageBatch(operationId, progress);
  }

  @Job(name = "BudgetGuessr licensed candidate image batch", retries = 0)
  public void processImageBatch(String operationId, BudgetStageProgress progress) {
    try {
      console.heartbeatOperation(operationId);
      BudgetEnrichmentBatchResult result = enrichmentService.processImageBatch();
      if (result.claimed() == 0) {
        if (progress.failed() > 0) {
          completeWithFailures(operationId, progress);
          return;
        }
        console.success(
            "Licensed candidate images completed: "
                + progress.processed()
                + "/"
                + progress.total());
        completeOperation(operationId);
        return;
      }
      BudgetStageProgress updated = progress.advance(result);
      logMilestone(progress, updated);
      enqueueImageBatch(operationId, updated);
    } catch (RuntimeException exception) {
      failOperation(operationId, exception);
    }
  }

  private void enqueueImageBatch(String operationId, BudgetStageProgress progress) {
    jobScheduler.enqueue(() -> processImageBatch(operationId, progress));
  }

  private void logMilestone(BudgetStageProgress previous, BudgetStageProgress updated) {
    if (!updated.reachedNewMilestone(previous)) {
      return;
    }
    stageInfo(
        "Licensed candidate images"
            + ": "
            + updated.processed()
            + "/"
            + updated.total()
            + " ("
            + updated.milestonePercent()
            + "%), "
            + updated.failed()
            + " failed");
  }

  private void stageInfo(String message) {
    console.info(message);
    log.info(message);
  }

  private void completeOperation(String operationId) {
    console.success("Licensed candidate image enrichment completed");
    console.completeOperation(operationId);
  }

  private void completeWithFailures(String operationId, BudgetStageProgress progress) {
    logFailedCandidates(progress);
    String error =
        "Licensed candidate image enrichment completed with %d failed candidates"
            .formatted(progress.failed());
    console.error(error);
    console.failOperation(operationId, error);
    log.warn("[op={}] {}", operationId, error);
  }

  private void logFailedCandidates(BudgetStageProgress progress) {
    List<BudgetCandidateImageFailure> failures =
        enrichmentService.findFailedImageCandidates(MAX_LOGGED_IMAGE_FAILURES);
    failures.forEach(
        failure ->
            console.error(
                "Image failed: %s (%s, %s): %s"
                    .formatted(
                        failure.name(),
                        failure.wikidataId(),
                        failure.category(),
                        failure.failureReason())));
    int unlisted = progress.failed() - failures.size();
    if (unlisted > 0) {
      console.error("Image failed: %d more candidates not listed".formatted(unlisted));
    }
  }

  private void failOperation(String operationId, RuntimeException exception) {
    String error = conciseMessage(exception);
    console.error("Image enrichment" + " failed: " + error);
    console.failOperation(operationId, error);
    log.error("[op={}] {} failed", operationId, "Image enrichment", exception);
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
    return message.length() <= 300 ? message : message.substring(0, 297) + "...";
  }
}
