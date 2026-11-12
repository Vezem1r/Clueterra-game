package io.clueterra.questions.quizgame.model;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record QuizQuestionDraft(
        String kind,
        double score,
        int level,
        JsonNode subject,
        List<JsonNode> options,
        int answerIndex,
        JsonNode fact,
        String contentHash) {

    public QuizQuestionDraft atLevel(int assigned) {
        return new QuizQuestionDraft(kind, score, assigned, subject, options, answerIndex, fact, contentHash);
    }
}
