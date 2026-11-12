package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.embedding.HintEmbeddingValue;
import io.clueterra.questions.ai.model.embedding.PendingHintEmbedding;
import io.clueterra.questions.ai.repository.AiRunRepository;
import io.clueterra.questions.ai.repository.HintEmbeddingRepository;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.embedding.LocalEmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHintEmbeddingServiceTest {

    private static final long COUNTRY_ID = 41L;
    private static final String MODEL = "test-model";

    @Mock
    private HintEmbeddingRepository embeddingRepository;
    @Mock
    private AiRunRepository runRepository;
    @Mock
    private LocalEmbeddingClient embeddingClient;

    private AiHintEmbeddingService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setModel(MODEL);
        properties.getEmbedding().setOutputDimensionality(2);
        properties.getEmbedding().setBatchSize(2);
        service = new AiHintEmbeddingService(
                properties,
                embeddingRepository,
                runRepository,
                embeddingClient,
                Clock.systemUTC());
    }

    @Test
    void backfillsOneCountryInConfiguredBatches() {
        List<PendingHintEmbedding> pending = List.of(
                new PendingHintEmbedding(1L, "First clue", "revision-1"),
                new PendingHintEmbedding(2L, "Second clue", "revision-2"),
                new PendingHintEmbedding(3L, "Third clue", "revision-3"));
        when(embeddingRepository.findHintsPendingEmbedding(COUNTRY_ID, MODEL, 2)).thenReturn(pending);
        when(embeddingClient.embed(List.of("First clue", "Second clue")))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}, new float[]{0.0f, 1.0f}));
        when(embeddingClient.embed(List.of("Third clue")))
                .thenReturn(List.of(new float[]{0.5f, 0.5f}));

        int stored = service.backfillCountry(COUNTRY_ID);

        assertThat(stored).isEqualTo(3);
        ArgumentCaptor<List<HintEmbeddingValue>> batches = ArgumentCaptor.forClass(List.class);
        verify(embeddingRepository, times(2)).upsertBatch(batches.capture(), eq(MODEL));
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(2, 1);
        assertThat(batches.getAllValues().stream().flatMap(List::stream).map(HintEmbeddingValue::hintId))
                .containsExactly(1L, 2L, 3L);
    }
}
