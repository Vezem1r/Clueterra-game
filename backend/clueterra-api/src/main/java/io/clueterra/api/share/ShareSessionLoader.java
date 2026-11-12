package io.clueterra.api.share;

import java.util.Optional;

import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.profile.repository.ProfileRow;
import io.clueterra.api.share.dto.CountrySquare;
import io.clueterra.api.share.dto.ShareData;
import org.springframework.stereotype.Service;

import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionRow;
import io.clueterra.api.profile.repository.ProfileJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShareSessionLoader {

    private final GameSessionJdbcRepository sessionRepository;
    private final ProfileJdbcRepository profileRepository;

    public Optional<ShareData> load(long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .filter(row -> row.status() == SessionStatus.COMPLETED)
                .map(this::toShareData);
    }

    private ShareData toShareData(SessionRow row) {
        var squares = row.countries().stream()
                .map(c -> new CountrySquare(c.isGuessed(), c.hintsRevealed()))
                .toList();

        long activeTimeMs = row.countries().stream().mapToLong(SessionCountryRow::timeSpentMs).sum();
        int totalHintsSeen = row.countries().stream().mapToInt(SessionCountryRow::hintsRevealed).sum();
        int totalHintsAvailable = row.countries().stream().mapToInt(SessionCountryRow::totalHints).sum();
        int guessedCountries = (int) row.countries().stream().filter(SessionCountryRow::isGuessed).count();

        int streak =
                Optional.ofNullable(row.userId())
                        .flatMap(profileRepository::findOwnProfile)
                        .map(ProfileRow::currentStreak)
                        .orElse(0);

        return new ShareData(
                row.challengeDate(),
                row.challengeLevel(),
                guessedCountries,
                row.countries().size(),
                activeTimeMs,
                row.totalXpEarned(),
                totalHintsSeen,
                totalHintsAvailable,
                streak,
                squares);
    }
}
