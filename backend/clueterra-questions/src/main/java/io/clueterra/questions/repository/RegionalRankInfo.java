package io.clueterra.questions.repository;

import java.math.BigDecimal;

public record RegionalRankInfo(Long countryId, String countryName, int rank, BigDecimal value) {
}
