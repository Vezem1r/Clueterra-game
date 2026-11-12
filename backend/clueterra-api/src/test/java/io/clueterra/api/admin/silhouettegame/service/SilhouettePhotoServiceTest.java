package io.clueterra.api.admin.silhouettegame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.admin.hints.image.DownloadedWikimediaImage;
import io.clueterra.api.admin.hints.image.WikimediaCommonsImageClient;
import io.clueterra.api.admin.hints.image.WikimediaHintImage;
import io.clueterra.api.admin.hints.image.WikimediaImageException;
import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.clueterra.api.admin.silhouettegame.client.CommonsLandmarkClient;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoChangeResponse;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoFollowUp;
import io.clueterra.api.admin.silhouettegame.dto.SilhouettePhotoReplaceRequest;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewDto;
import io.clueterra.api.admin.silhouettegame.model.SilhouetteLandmark;
import io.clueterra.api.admin.silhouettegame.model.SilhouettePhotoUpdate;
import io.clueterra.api.admin.silhouettegame.repository.SilhouetteGeneratedContentJdbcRepository;
import io.clueterra.api.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class SilhouettePhotoServiceTest {

    private static final String TITLE = "Taj Mahal (Edited).jpeg";

    @Mock
    private SilhouetteGeneratedContentJdbcRepository repository;
    @Mock
    private WikimediaCommonsImageClient wikimediaImageClient;
    @Mock
    private CommonsLandmarkClient landmarkClient;
    @Mock
    private StorageService storageService;
    @Mock
    private ClueterraQuestionsClient questionsClient;

    @InjectMocks
    private SilhouettePhotoService service;

    @Test
    void aCommonsPageLinkBecomesAManualPhotoInTheMediaLibrary() {
        WikimediaHintImage image = image("image/jpeg", 3000, 2000);
        when(repository.findQuestion("IN")).thenReturn(Optional.of(question(null, "AUTO")),
                Optional.of(question("/api/assets/countries/photos/x.jpg", "MANUAL")));
        when(wikimediaImageClient.findExactImage(TITLE)).thenReturn(image);
        when(landmarkClient.depicted(TITLE)).thenReturn(Optional.of("Q9141"));
        when(landmarkClient.landmark("Q9141"))
                .thenReturn(Optional.of(new SilhouetteLandmark("Q9141", Map.of("en", "Taj Mahal"), 185)));
        when(wikimediaImageClient.downloadThumbnail(image))
                .thenReturn(new DownloadedWikimediaImage(new byte[]{1, 2, 3}, "image/jpeg", "jpg"));

        SilhouettePhotoChangeResponse response = service.replace("IN", new SilhouettePhotoReplaceRequest(
                "https://commons.wikimedia.org/wiki/File:Taj_Mahal_%28Edited%29.jpeg", null), "admin");

        assertThat(response.followUp()).isEqualTo(SilhouettePhotoFollowUp.CLUES_REGENERATING);
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(storageService).uploadToHintBucket(objectKey.capture(), any(InputStream.class), eq(3L), eq("image/jpeg"));
        assertThat(objectKey.getValue()).matches("country-photos/[0-9a-f]{64}\\.jpg");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Optional<SilhouettePhotoUpdate>> saved = ArgumentCaptor.forClass(Optional.class);
        verify(repository).saveManual(eq("IN"), saved.capture(), eq("admin"));
        SilhouettePhotoUpdate update = saved.getValue().orElseThrow();
        assertThat(update.objectKey()).isEqualTo(objectKey.getValue());
        assertThat(update.landmarkQid()).isEqualTo("Q9141");
        assertThat(update.licenseLabel()).isEqualTo("CC BY-SA 4.0");
        assertThat(update.contentSha256()).hasSize(64);
        verify(questionsClient).fireAndForget(anyString(), eq("/api/admin/silhouettes/generate"));
    }

    @Test
    void unsuitableFilesAreRejectedBeforeAnythingIsDownloaded() {
        when(repository.findQuestion("IN")).thenReturn(Optional.of(question(null, "AUTO")));
        when(wikimediaImageClient.findExactImage(TITLE)).thenReturn(image("image/png", 3000, 2000));

        assertThatThrownBy(() -> service.replace("IN", new SilhouettePhotoReplaceRequest("File:" + TITLE, null), "admin"))
                .isInstanceOf(WikimediaImageException.class)
                .hasMessageContaining("Only JPEG");

        when(wikimediaImageClient.findExactImage(TITLE)).thenReturn(image("image/jpeg", 4000, 800));
        assertThatThrownBy(() -> service.replace("IN", new SilhouettePhotoReplaceRequest(TITLE, null), "admin"))
                .isInstanceOf(WikimediaImageException.class)
                .hasMessageContaining("too narrow or too wide");

        verify(wikimediaImageClient, never()).downloadThumbnail(any());
        verify(storageService, never()).uploadToHintBucket(anyString(), any(), anyLong(), anyString());
        verify(repository, never()).saveManual(anyString(), any(), anyString());
    }

    @Test
    void returningToTheAutomaticChoiceReleasesTheCountryAndStartsTheSync() {
        when(repository.findQuestion("IN")).thenReturn(Optional.of(question(null, "MANUAL")), Optional.of(question(null, "AUTO")));
        when(repository.release("IN")).thenReturn(true);

        SilhouettePhotoChangeResponse response = service.automatic("IN");

        assertThat(response.followUp()).isEqualTo(SilhouettePhotoFollowUp.SYNC_STARTED);
        assertThat(response.question().photoSelection()).isEqualTo("AUTO");
        verify(questionsClient).fireAndForget(anyString(), eq("/api/admin/countries/photos/sync"));
    }

    private WikimediaHintImage image(String mimeType, int width, int height) {
        return new WikimediaHintImage("File:" + TITLE, "1".repeat(40), "https://upload.wikimedia.org/thumb/taj.jpeg",
                "https://upload.wikimedia.org/taj.jpeg", "https://commons.wikimedia.org/wiki/File:Taj_Mahal_(Edited).jpeg",
                "Yann", "Image by Yann via Wikimedia Commons.", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0", mimeType, 400_000L, width, height);
    }

    private SilhouetteQuestionPreviewDto question(String photoUrl, String selection) {
        return new SilhouetteQuestionPreviewDto(7L, "IN", "India", 1, "Asia", "Southern Asia", "M0 0Z", true, "{}", List.of(),
                photoUrl, null, null, null, null, null, null, selection, "MANUAL".equals(selection) ? "admin" : null,
                null, null, null, 0, 0, 0, 0, null, null);
    }
}
