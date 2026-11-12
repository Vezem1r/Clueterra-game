package io.clueterra.questions.service.hint;

import io.clueterra.questions.repository.HintUpsertAction;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HintLifecycleServiceTest {

    @Test
    void archivesStaleHintsByLogicalIdentity() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);

        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        Country country = Country.builder()
                .id(77L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CandidateHint candidate = CandidateHint.builder()
                .text("Fresh hint")
                .type(HintType.CULTURAL)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_ACTIVE")
                .estimatedDifficulty(20)
                .estimatedQuality(new BigDecimal("0.90"))
                .ambiguityCount(1)
                .build();

        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(candidate.text());
        when(deduplicationService.generateContentHash(candidate.text())).thenReturn("hash-fresh");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of(
                hint(1L, "MANUAL_OLD"),
                hint(2L, "MANUAL_ACTIVE")
        ));

        SourceOwnedLifecycleResult result = service.applySourceOwnedLifecycle(
                country,
                List.of(candidate),
                Set.of(DataSource.MANUAL)
        );

        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.accepted()).containsExactly(candidate);
        verify(hintRepository).archiveAndDeleteByIds(List.of(1L), "stale_source_regen");
    }

    @Test
    void keepsOnlyHighestQualityCandidatePerLogicalIdentity() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        Country country = Country.builder()
                .id(77L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CandidateHint weaker = CandidateHint.builder()
                .text("Lower quality")
                .type(HintType.CULTURAL)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_ACTIVE")
                .estimatedDifficulty(20)
                .estimatedQuality(new BigDecimal("0.70"))
                .ambiguityCount(2)
                .build();
        CandidateHint stronger = CandidateHint.builder()
                .text("Higher quality")
                .type(HintType.CULTURAL)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_ACTIVE")
                .estimatedDifficulty(18)
                .estimatedQuality(new BigDecimal("0.95"))
                .ambiguityCount(1)
                .build();

        when(hintSanitizer.sanitize(stronger.text(), country)).thenReturn(stronger.text());
        when(deduplicationService.generateContentHash(stronger.text())).thenReturn("hash-strong");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of());

        SourceOwnedLifecycleResult result = service.applySourceOwnedLifecycle(
                country,
                List.of(weaker, stronger),
                Set.of(DataSource.MANUAL)
        );

        assertThat(result.accepted()).containsExactly(stronger);
        verify(hintRepository).saveWithArchive(any(Hint.class));
    }

    @Test
    void saveWithArchive_returnsNullWhenSanitizerRejectsHint() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        Country country = Country.builder().id(77L).isoAlpha2("US").nameEn("United States").build();
        CandidateHint candidate = CandidateHint.builder()
                .text("Leaky hint")
                .source(DataSource.MANUAL)
                .estimatedDifficulty(20)
                .estimatedQuality(new BigDecimal("0.90"))
                .build();

        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(null);

        assertThat(service.saveWithArchive(country, candidate)).isNull();
        verify(hintRepository, never()).saveWithArchive(any(Hint.class));
    }

    @Test
    void archivesAllExistingHintsForSourceWhenNoCandidatesRemain() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);

        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        Country country = Country.builder()
                .id(77L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of(
                hint(1L, "MANUAL_OLD_A"),
                hint(2L, "MANUAL_OLD_B")
        ));

        SourceOwnedLifecycleResult result = service.applySourceOwnedLifecycle(
                country,
                List.of(),
                Set.of(DataSource.MANUAL)
        );

        assertThat(result.accepted()).isEmpty();
        verify(hintRepository).archiveAndDeleteByIds(List.of(1L, 2L), "stale_source_regen");
    }

    @Test
    void archivesBudgetHintAfterProjectDeactivationEvenWhenTextWasEdited() {
        HintRepository hintRepository = mock(HintRepository.class);
        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(mock(HintSanitizer.class), mock(DeduplicationService.class)));
        Country country = Country.builder().id(77L).isoAlpha2("US").nameEn("United States").build();
        Hint editedBudgetHint = Hint.builder()
                .id(3L)
                .countryId(77L)
                .category(Category.INFRASTRUCTURE)
                .hintType(HintType.IMAGE_FACT)
                .source(DataSource.BUDGET_PROJECTS)
                .sourceIndicator("BUDGET_PROJECT_Q123")
                .editorialTextLocked(true)
                .isActive(true)
                .build();
        when(hintRepository.findActiveByCountryIdAndSource(77L, DataSource.BUDGET_PROJECTS))
                .thenReturn(List.of(editedBudgetHint));

        service.applySourceOwnedLifecycle(country, List.of(), Set.of(DataSource.BUDGET_PROJECTS));

        verify(hintRepository).archiveAndDeleteByIds(List.of(3L), "stale_source_regen");
    }

    @Test
    void countsUpdatedHintsSeparatelyWhenRepositoryUpsertsExistingContent() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        HintLifecycleService service = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        Country country = Country.builder()
                .id(77L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CandidateHint candidate = CandidateHint.builder()
                .text("Stable hint")
                .type(HintType.CULTURAL)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_ACTIVE")
                .estimatedDifficulty(20)
                .estimatedQuality(new BigDecimal("0.90"))
                .ambiguityCount(1)
                .build();

        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(candidate.text());
        when(deduplicationService.generateContentHash(candidate.text())).thenReturn("hash-stable");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.SOFT_UPDATED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of(
                hint(2L, "MANUAL_ACTIVE")
        ));

        SourceOwnedLifecycleResult result = service.applySourceOwnedLifecycle(
                country,
                List.of(candidate),
                Set.of(DataSource.MANUAL)
        );

        assertThat(result.newCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.accepted()).containsExactly(candidate);
    }

    private Hint hint(Long id, String sourceIndicator) {
        return Hint.builder()
                .id(id)
                .countryId(77L)
                .category(Category.CULTURE)
                .hintType(HintType.CULTURAL)
                .hintText("hint-" + id)
                .difficulty(20)
                .qualityScore(new BigDecimal("0.80"))
                .source(DataSource.MANUAL)
                .sourceIndicator(sourceIndicator)
                .contentHash("hash-" + id)
                .isActive(true)
                .build();
    }
}
