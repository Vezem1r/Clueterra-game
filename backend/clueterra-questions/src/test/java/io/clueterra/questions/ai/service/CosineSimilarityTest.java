package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.service.embedding.CosineSimilarity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        float[] vector = {0.2f, -0.5f, 0.84f};
        assertThat(CosineSimilarity.between(vector, vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertThat(CosineSimilarity.between(new float[]{1f, 0f}, new float[]{0f, 1f})).isZero();
    }

    @Test
    void magnitudeDoesNotAffectDirection() {
        double similarity = CosineSimilarity.between(new float[]{1f, 2f, 3f}, new float[]{10f, 20f, 30f});
        assertThat(similarity).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void mismatchedOrEmptyVectorsScoreZero() {
        assertThat(CosineSimilarity.between(new float[]{1f, 2f}, new float[]{1f})).isZero();
        assertThat(CosineSimilarity.between(new float[0], new float[0])).isZero();
        assertThat(CosineSimilarity.between(null, new float[]{1f})).isZero();
        assertThat(CosineSimilarity.between(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
    }
}
