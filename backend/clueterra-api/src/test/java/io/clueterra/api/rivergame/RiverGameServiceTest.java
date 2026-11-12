package io.clueterra.api.rivergame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.rivergame.dto.RiverAnswerDto;
import io.clueterra.api.rivergame.dto.RiverGuessResultDto;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.rivergame.dto.RiverQuestionDto;
import io.clueterra.api.rivergame.dto.RiverRevealDto;
import io.clueterra.api.rivergame.dto.RiverSessionDto;
import io.clueterra.api.rivergame.model.RiverGuessRow;
import io.clueterra.api.rivergame.model.RiverQuestionRow;
import io.clueterra.api.rivergame.model.RiverRevealRow;
import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.rivergame.service.RiverGameService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiverGameServiceTest {

    private static final String HINTS = "[{\"position\":1,\"kind\":\"GEOGRAPHY\",\"text\":\"North Sea\",\"place\":null}]";
    private static final RiverPlayerStatsDto STATS = new RiverPlayerStatsDto(3, 2, 5, 0, 340, 42L);

    @Test
    void onlyRevealsTheBorderMapUrlAfterTheSecondHint() {
        RiverGameJdbcRepository repository = mock(RiverGameJdbcRepository.class);
        RiverGameService service = new RiverGameService(repository, mock(AuthJdbcRepository.class),
                mock(LegalAcceptanceService.class), mock(TemplateBadgeEngine.class));
        String plainKey = "river-game/" + "a".repeat(64) + ".webp";
        String bordersKey = "river-game/" + "b".repeat(64) + ".webp";
        String course = "{\"bbox\":[7,47,8,52],\"coordinates\":[[[8,47],[7,52]]]}";
        when(repository.findCurrent(7L))
                .thenReturn(Optional.of(new RiverQuestionRow(42L, (short) 1, plainKey, bordersKey, course,
                        (short) 1, 0, 1L, false, HINTS, 200, 15)))
                .thenReturn(Optional.of(new RiverQuestionRow(42L, (short) 1, plainKey, bordersKey, course,
                        (short) 2, 0, 1L, false, HINTS, 200, 15)));

        when(repository.findPlayerStats(7L)).thenReturn(STATS);

        RiverSessionDto session = service.session(7L);
        RiverQuestionDto first = session.current();
        assertThat(session.stats()).isEqualTo(STATS);
        assertThat(first.imageUrl()).isEqualTo("/assets/rivers/" + "a".repeat(64) + ".webp");
        assertThat(first.course()).isEqualTo(course);
        assertThat(first.maximumXp()).isEqualTo(170);
        assertThat(first.cluePenaltyXp()).isEqualTo(30);
        assertThat(first.revealedHints()).isEqualTo(HINTS);
        assertThat(service.session(7L).current().imageUrl())
                .isEqualTo("/assets/rivers/" + "b".repeat(64) + ".webp");
    }

    @Test
    void correctGuessAndShowAnswerRevealTheRiverWithItsPhoto() {
        RiverGameJdbcRepository repository = mock(RiverGameJdbcRepository.class);
        AuthJdbcRepository authRepository = mock(AuthJdbcRepository.class);
        LegalAcceptanceService legal = mock(LegalAcceptanceService.class);
        RiverGameService service = new RiverGameService(repository, authRepository, legal,
                mock(TemplateBadgeEngine.class));
        when(authRepository.findById(7L)).thenReturn(Optional.of(new UserRow(7L, "p@example.com", "hash", "p",
                "USER", true, null, false, null, null, "t", "p", false, null)));
        when(legal.isCurrent("t", "p")).thenReturn(true);
        when(repository.guess(7L, 42L, "Rhine")).thenReturn(new RiverGuessRow(true, 200, 1));
        when(repository.findPlayerStats(7L)).thenReturn(STATS);
        String facts = "[{\"kind\":\"LENGTH\",\"text\":\"Its reported length is about 1,230 km.\"}]";
        when(repository.findReveal(42L)).thenReturn(Optional.of(new RiverRevealRow(42L, "Rhine", "DE", 1230,
                "river-game/" + "b".repeat(64) + ".webp", "https://www.wikidata.org/wiki/Q584", facts,
                "https://en.wikipedia.org/wiki/Rhine", "river-photos/" + "c".repeat(64) + ".jpg",
                "Jane Doe", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0",
                "https://commons.wikimedia.org/wiki/File:Loreley.jpg")));

        RiverGuessResultDto solved = service.guess(7L, 42L, "Rhine");
        RiverAnswerDto answer = service.showAnswer(7L, 42L);
        RiverRevealDto shown = answer.reveal();

        assertThat(solved.reveal()).isEqualTo(shown);
        assertThat(solved.stats()).isEqualTo(STATS);
        assertThat(answer.stats()).isEqualTo(STATS);
        assertThat(shown.mapImageUrl()).isEqualTo("/assets/rivers/" + "b".repeat(64) + ".webp");
        assertThat(shown.photo().url()).isEqualTo("/assets/rivers/photos/" + "c".repeat(64) + ".jpg");
        assertThat(shown.photo().licenseLabel()).isEqualTo("CC BY-SA 4.0");
        assertThat(shown.facts()).isEqualTo(facts);
    }

    @Test
    void guestWithoutAGameStartsFromEmptyStats() {
        RiverGameJdbcRepository repository = mock(RiverGameJdbcRepository.class);
        RiverGameService service = new RiverGameService(repository, mock(AuthJdbcRepository.class),
                mock(LegalAcceptanceService.class), mock(TemplateBadgeEngine.class));

        RiverSessionDto session = service.session(null);

        assertThat(session.current()).isNull();
        assertThat(session.stats().attemptedRivers()).isZero();
        verify(repository, never()).findPlayerStats(any(Long.class));
    }

    @Test
    void wrongGuessDoesNotLeakTheAnswer() {
        RiverGameJdbcRepository repository = mock(RiverGameJdbcRepository.class);
        AuthJdbcRepository authRepository = mock(AuthJdbcRepository.class);
        LegalAcceptanceService legal = mock(LegalAcceptanceService.class);
        RiverGameService service = new RiverGameService(repository, authRepository, legal,
                mock(TemplateBadgeEngine.class));
        when(authRepository.findById(7L)).thenReturn(Optional.of(new UserRow(7L, "p@example.com", "hash", "p",
                "USER", true, null, false, null, null, "t", "p", false, null)));
        when(legal.isCurrent("t", "p")).thenReturn(true);
        when(repository.guess(7L, 42L, "Danube")).thenReturn(new RiverGuessRow(false, 0, 1));
        when(repository.findPlayerStats(7L)).thenReturn(STATS);

        RiverGuessResultDto result = service.guess(7L, 42L, "Danube");

        assertThat(result.reveal()).isNull();
        assertThat(result.stats()).isEqualTo(STATS);
        verify(repository, never()).findReveal(any(Long.class));
    }
}
