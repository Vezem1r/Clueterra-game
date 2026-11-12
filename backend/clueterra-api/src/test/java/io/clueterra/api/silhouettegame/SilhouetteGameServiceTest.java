package io.clueterra.api.silhouettegame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.silhouettegame.dto.SilhouetteClueDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteQuestionDto;
import io.clueterra.api.silhouettegame.model.SilhouettePhotoRow;
import io.clueterra.api.silhouettegame.model.SilhouettePlayerSessionRow;
import io.clueterra.api.silhouettegame.model.SilhouetteQuestionRow;
import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;
import io.clueterra.api.silhouettegame.service.SilhouetteGameService;

@ExtendWith(MockitoExtension.class)
class SilhouetteGameServiceTest {

    private static final long USER_ID = 7L;
    private static final String REGION = """
            {"position":1,"kind":"REGION","costUnits":1,"payload":{"region":"Europe","subregion":"Western Europe","center":[8.0,49.0]}}""";
    private static final String LOCATION = """
            {"position":2,"kind":"LOCATION","costUnits":1,"payload":{"center":[10.4,51.1],"bounds":[5.9,47.3,15.0,55.1]}}""";
    private static final String CAPITAL = """
            {"position":3,"kind":"CAPITAL","costUnits":2,"payload":{"capitals":[{"en":"Berlin","ru":"Берлин","cs":"Berlín"}]}}""";
    private static final String FLAG_COLORS = """
            {"position":3,"kind":"FLAG_COLORS","costUnits":2,"payload":{"colors":[{"color":"#000000","share":0.5}]}}""";
    private static final String LANDMARK = """
            {"position":4,"kind":"LANDMARK","costUnits":2,"payload":{}}""";
    private static final SilhouettePlayerStatsDto STATS = new SilhouettePlayerStatsDto(0, 0, 197, 0, 0, 0);
    private static final SilhouettePhotoRow PHOTO = new SilhouettePhotoRow("country-photos/" + "a".repeat(64) + ".jpg",
            "Author", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/",
            "https://commons.wikimedia.org/wiki/File:Brandenburger_Tor.jpg", "{\"en\":\"Brandenburg Gate\"}");

    @Mock
    private SilhouetteGameJdbcRepository repository;
    @Mock
    private AuthJdbcRepository authRepository;
    @Mock
    private LegalAcceptanceService legalAcceptanceService;
    @Mock
    private TemplateBadgeEngine badgeEngine;

    private SilhouetteGameService service;

    @BeforeEach
    void setUp() {
        service = new SilhouetteGameService(repository, authRepository, legalAcceptanceService, badgeEngine, new ObjectMapper());
        when(repository.findPlayerSession(USER_ID)).thenReturn(new SilhouettePlayerSessionRow(STATS, List.of()));
    }

    @Test
    void unopenedCluesAreNeverSentToThePlayer() {
        when(repository.findCurrent(USER_ID)).thenReturn(Optional.of(row(1, false, PHOTO, REGION, LOCATION, CAPITAL, LANDMARK)));

        SilhouetteQuestionDto question = service.session(USER_ID).current();

        assertThat(question.hints().region().subregion()).isEqualTo("Western Europe");
        assertThat(question.hints().region().center()).containsExactly(8.0, 49.0);
        assertThat(question.hints().location()).isNull();
        assertThat(question.hints().capitals()).isNull();
        assertThat(question.hints().landmark()).isNull();
        assertThat(question.clues()).extracting(SilhouetteClueDto::costXp).containsExactly(12, 12, 24, 24);
        assertThat(question.maximumXp()).isEqualTo(138);
        verifyNoInteractions(badgeEngine);
    }

    @Test
    void theLandmarkClueShowsThePhotoWithoutNamingIt() {
        when(repository.findCurrent(USER_ID)).thenReturn(Optional.of(row(4, true, PHOTO, REGION, LOCATION, CAPITAL, LANDMARK)));

        SilhouetteQuestionDto question = service.session(USER_ID).current();

        assertThat(question.hints().location().center()).containsExactly(10.4, 51.1);
        assertThat(question.hints().capitals()).contains("Berlin");
        assertThat(question.hints().landmark().url()).isEqualTo("/api/assets/countries/photos/" + "a".repeat(64) + ".jpg");
        assertThat(question.hints().landmark().authorName()).isEqualTo("Author");
        assertThat(question.maximumXp()).isZero();
    }

    @Test
    void flagColoursReplaceTheCapitalWithoutRevealingItInAdvance() {
        when(repository.findCurrent(USER_ID)).thenReturn(Optional.of(row(3, false, null, REGION, LOCATION, FLAG_COLORS)));

        SilhouetteQuestionDto question = service.session(USER_ID).current();

        assertThat(question.hints().capitals()).isNull();
        assertThat(question.hints().flagColors()).contains("#000000");
        assertThat(question.clues()).extracting(SilhouetteClueDto::kind).containsExactly("REGION", "LOCATION", "CAPITAL");
        assertThat(question.maximumXp()).isEqualTo(102);
    }

    private static SilhouetteQuestionRow row(int hintsUsed, boolean rewarded, SilhouettePhotoRow photo, String... hints) {
        return new SilhouetteQuestionRow(3, 1, "DE", "M1 1 2 2 3 1Z", "[" + String.join(",", hints) + "]",
                hintsUsed, 0, rewarded, 150, 8, photo);
    }
}
