package io.clueterra.api.quizgame.model;

import io.clueterra.api.quizgame.dto.QuizSessionDto;

public record QuizGuestStartResult(String guestToken, QuizSessionDto session) {
}
