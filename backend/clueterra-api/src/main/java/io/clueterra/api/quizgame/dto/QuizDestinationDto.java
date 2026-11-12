package io.clueterra.api.quizgame.dto;

import java.util.List;

public record QuizDestinationDto(QuizCountryDto country, String mode, int level, List<String> route) {
}
