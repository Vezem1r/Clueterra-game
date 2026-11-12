package io.clueterra.questions.service.deduplication;

import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.service.deduplication.dto.DeduplicationResult;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeduplicationServiceTest {

    @Test
    void generateContentHash_changesWhenPunctuationChanges() {
        DeduplicationService service = new DeduplicationService(mock(HintRepository.class));

        String withSemicolon = service.generateContentHash("Borders: France; Spain");
        String withComma = service.generateContentHash("Borders: France, Spain");

        assertThat(withSemicolon).isNotEqualTo(withComma);
    }

    @Test
    void generateContentHash_changesWhenNumbersChange() {
        DeduplicationService service = new DeduplicationService(mock(HintRepository.class));

        String lower = service.generateContentHash("This country has 26 UNESCO sites.");
        String higher = service.generateContentHash("This country has 27 UNESCO sites.");

        assertThat(lower).isNotEqualTo(higher);
    }

    @Test
    void deduplicate_marksExactRenderedTextAsRefreshEvenWithLegacyStoredHash() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService service = new DeduplicationService(hintRepository);

        CandidateHint candidate = CandidateHint.builder()
                .text("The seat of government is Washington, D.C.")
                .estimatedQuality(new BigDecimal("0.90"))
                .build();

        when(hintRepository.findContentHashesByCountryId(77L)).thenReturn(Set.of("legacy-hash"));
        when(hintRepository.findActiveTextsByCountryId(77L))
                .thenReturn(List.of("The seat of government is Washington, D.C."));

        DeduplicationResult result = service.deduplicate(77L, List.of(candidate));

        assertThat(result.accepted()).isEmpty();
        assertThat(result.toRefresh()).containsExactly(candidate);
        assertThat(result.rejected()).isEmpty();
    }
}
