package io.clueterra.api.share.dto;

import java.time.LocalDate;
import java.util.List;

public record ShareData(
        LocalDate date,
        String level,
        int guessedCountries,
        int totalCountries,
        long activeTimeMs,
        int totalXpEarned,
        int totalHintsSeen,
        int totalHintsAvailable,
        int streak,
        List<CountrySquare> squares) {
}
