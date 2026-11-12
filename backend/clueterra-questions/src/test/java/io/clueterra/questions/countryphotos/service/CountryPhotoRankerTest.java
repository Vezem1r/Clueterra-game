package io.clueterra.questions.countryphotos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.clueterra.questions.countryphotos.model.CommonsFileInfo;
import io.clueterra.questions.countryphotos.model.CountryPhotoCandidate;
import io.clueterra.questions.countryphotos.model.LandmarkInfo;
import io.clueterra.questions.countryphotos.model.RankedCountryPhoto;

class CountryPhotoRankerTest {

    private static final String PAGE = "https://commons.wikimedia.org/wiki/File:";

    private final CountryPhotoRanker ranker = new CountryPhotoRanker();

    @Test
    void theBestKnownLandmarkWinsEvenInPortraitAndSmallOrUnsuitableFilesAreDropped() {
        List<CountryPhotoCandidate> candidates = List.of(
                candidate("Mus%C3%A9e_d%27Orsay_(22284728710).jpg"),
                candidate("Tour_Eiffel_Wikimedia_Commons.jpg"),
                candidate("Tiny_photo.jpg"),
                candidate("Diagram.png"));
        Map<String, CommonsFileInfo> files = Map.of(
                "Musée d'Orsay (22284728710).jpg", file("Musée d'Orsay (22284728710).jpg", 1, 9490, 5023, "image/jpeg"),
                "Tour Eiffel Wikimedia Commons.jpg", file("Tour Eiffel Wikimedia Commons.jpg", 2, 2900, 5367, "image/jpeg"),
                "Tiny photo.jpg", file("Tiny photo.jpg", 3, 640, 480, "image/jpeg"),
                "Diagram.png", file("Diagram.png", 4, 3000, 2000, "image/png"));
        Map<Long, String> depicted = Map.of(1L, "Q23402", 2L, "Q243");
        Map<String, LandmarkInfo> landmarks = Map.of(
                "Q23402", new LandmarkInfo("Q23402", Map.of("en", "Musée d'Orsay"), 70),
                "Q243", new LandmarkInfo("Q243", Map.of("en", "Eiffel Tower"), 191));

        List<RankedCountryPhoto> ranked = ranker.rank(candidates, files, depicted, landmarks);

        assertThat(ranked).extracting(photo -> photo.file().title())
                .containsExactly("Tour Eiffel Wikimedia Commons.jpg", "Musée d'Orsay (22284728710).jpg");
        assertThat(ranked.getFirst().landmark().names()).containsEntry("en", "Eiffel Tower");
    }

    @Test
    void decodesCommonsPageUrlsIntoFileTitles() {
        assertThat(ranker.title(PAGE + "%C4%8Cesk%C3%BD_Krumlov_Castle.jpg")).isEqualTo("Český Krumlov Castle.jpg");
    }

    private static CountryPhotoCandidate candidate(String file) {
        return new CountryPhotoCandidate(1L, "FR", PAGE + file);
    }

    private static CommonsFileInfo file(String title, long pageId, int width, int height, String mime) {
        return new CommonsFileInfo(title, pageId, width, height, mime);
    }
}
