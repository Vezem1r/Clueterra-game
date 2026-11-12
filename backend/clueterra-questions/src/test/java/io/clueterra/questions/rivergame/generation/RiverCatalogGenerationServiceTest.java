package io.clueterra.questions.rivergame.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.storage.ImageStorageService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiverCatalogGenerationServiceTest {

    @Mock private RiverCatalogRepository repository;
    @Mock private ConsoleLogWriter console;
    @Mock private ImageStorageService imageStorageService;

    @Test
    void bundledCatalogHasValidMapsAndCanImportEveryEntry() {
        when(repository.findMissingCountryCodes(any())).thenReturn(Set.of());
        when(imageStorageService.storeBytes(any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RiverCatalogImportJob service = new RiverCatalogImportJob(
                mapper, repository, new RiverCatalogAssetStore(Optional.of(imageStorageService)), console);

        service.generate("river-test");

        verify(repository, atLeastOnce()).replaceCountry(anyString(), any());
        verify(repository).retireAbsentCountries(any());
        verify(imageStorageService, atLeastOnce()).storeBytes(any(byte[].class), anyString(), eq("image/webp"));
        verify(console).completeOperation("river-test");
    }

    @Test
    void invalidBundledMapDoesNotPartiallyReplaceCountries() {
        when(repository.findMissingCountryCodes(any())).thenReturn(Set.of());
        RiverCatalogAssetStore assetStore = mock(RiverCatalogAssetStore.class);
        doThrow(new IllegalStateException("broken resource")).when(assetStore).verify(anyString());
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RiverCatalogImportJob service = new RiverCatalogImportJob(mapper, repository, assetStore, console);

        service.generate("river-test");

        verify(repository, never()).replaceCountry(anyString(), any());
        verify(console).failOperation(eq("river-test"), anyString());
    }
}
