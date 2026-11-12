package io.clueterra.api.quizgame.model;

public record QuizAnswerOutcome(boolean correct, int answerIndex, int xpAwarded, boolean explored) {
}
