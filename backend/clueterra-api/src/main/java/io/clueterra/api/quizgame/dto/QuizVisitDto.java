package io.clueterra.api.quizgame.dto;

import java.util.List;

public record QuizVisitDto(long stop, String countryCode, String fromCountryCode, String mode, List<String> route, int level) {
}
