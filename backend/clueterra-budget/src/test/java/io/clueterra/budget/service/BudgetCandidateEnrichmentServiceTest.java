package io.clueterra.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.budget.config.BudgetEnrichmentProperties;
import io.clueterra.budget.project.BudgetCandidateImageWorkItem;
import io.clueterra.budget.project.BudgetCandidateStoredImage;
import io.clueterra.budget.project.BudgetEnrichmentBatchResult;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.repository.BudgetCandidateEnrichmentRepository;

@ExtendWith(MockitoExtension.class)
class BudgetCandidateEnrichmentServiceTest {

  @Mock private BudgetCandidateEnrichmentRepository enrichmentRepository;
  @Mock private WikidataCandidateImageService imageService;

  private BudgetCandidateEnrichmentService service;

  @BeforeEach
  void setUp() {
    service =
        new BudgetCandidateEnrichmentService(
            new BudgetEnrichmentProperties(2, Duration.ofMinutes(5)),
            enrichmentRepository,
            imageService);
  }

  @Test
  void continuesBatchWhenPublishingOneCandidateFails() {
    BudgetCandidateImageWorkItem invalidCandidate = candidate("Q1");
    BudgetCandidateImageWorkItem validCandidate = candidate("Q2");
    BudgetCandidateStoredImage image = storedImage();
    when(enrichmentRepository.claimImageBatch(any(Integer.class), any()))
        .thenReturn(List.of(invalidCandidate, validCandidate));
    when(imageService.downloadAndStore(invalidCandidate)).thenReturn(image);
    when(imageService.downloadAndStore(validCandidate)).thenReturn(image);
    doThrow(new IllegalArgumentException("Invalid milestone precision"))
        .when(enrichmentRepository)
        .publishImage(eq(invalidCandidate), eq(image), any());

    BudgetEnrichmentBatchResult result = service.processImageBatch();

    assertThat(result).isEqualTo(new BudgetEnrichmentBatchResult(2, 1, 1));
    verify(enrichmentRepository).failImage(invalidCandidate, "Invalid milestone precision");
    verify(enrichmentRepository).publishImage(eq(validCandidate), eq(image), any());
  }

  private BudgetCandidateImageWorkItem candidate(String wikidataId) {
    return new BudgetCandidateImageWorkItem(
        wikidataId,
        MegaprojectCategory.BRIDGE,
        "File:" + wikidataId + ".jpg",
        null,
        null,
        1);
  }

  private BudgetCandidateStoredImage storedImage() {
    return new BudgetCandidateStoredImage(
        1L,
        "sha1",
        "File:Project.jpg",
        "candidates/project.jpg",
        "https://upload.wikimedia.org/project.jpg",
        "https://commons.wikimedia.org/wiki/File:Project.jpg",
        "Photographer",
        "Credit",
        "CC BY-SA 4.0",
        "https://creativecommons.org/licenses/by-sa/4.0/",
        "Project",
        "image/jpeg",
        1024L,
        1600,
        900,
        90);
  }
}
