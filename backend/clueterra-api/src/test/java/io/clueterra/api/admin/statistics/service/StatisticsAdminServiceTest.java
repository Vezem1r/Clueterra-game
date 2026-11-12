package io.clueterra.api.admin.statistics.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.clueterra.api.admin.statistics.dto.CountryStatisticAdminDto;
import io.clueterra.api.admin.statistics.dto.UpdateStatisticRequest;
import io.clueterra.api.admin.statistics.repository.AdminStatisticsJdbcRepository;
import io.clueterra.api.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsAdminServiceTest {

    @Mock
    private AdminStatisticsJdbcRepository repository;

    @Mock
    private StorageService storageService;

    private StatisticsAdminService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsAdminService(repository, storageService);
    }

    @Test
    void listStatisticsReturnsBackendOwnedImageUrls() {
        CountryStatisticAdminDto statistic = statistic();
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findAll(
                null, null, null, null, null, null, null,
                "fetchedAt", "desc", pageable))
                .thenReturn(new PageImpl<>(List.of(statistic), pageable, 1));
        when(storageService.resolveStatisticAssetUrl(statistic.id(), statistic.imageUrl()))
                .thenReturn("/api/assets/statistics/84");

        CountryStatisticAdminDto result = service.listStatistics(
                        0, 20, null, null, null, null, null,
                        null, null, "fetchedAt", "desc")
                .getContent()
                .getFirst();

        assertThat(result.imageUrl()).isEqualTo("/api/assets/statistics/84");
        assertThat(result.originalImageUrl()).isEqualTo(statistic.originalImageUrl());
        assertThat(result.imageSourcePageUrl()).isEqualTo(statistic.imageSourcePageUrl());
    }

    @Test
    void updateStatisticAlsoReturnsResolvedImageUrl() {
        CountryStatisticAdminDto statistic = statistic();
        UpdateStatisticRequest request = new UpdateStatisticRequest(
                null, "Updated", null, null, null);
        when(repository.update(statistic.id(), request)).thenReturn(Optional.of(statistic));
        when(storageService.resolveStatisticAssetUrl(statistic.id(), statistic.imageUrl()))
                .thenReturn("/api/assets/statistics/84");

        CountryStatisticAdminDto result = service.updateStatistic(statistic.id(), request)
                .orElseThrow();

        assertThat(result.imageUrl()).isEqualTo("/api/assets/statistics/84");
        verify(repository).update(statistic.id(), request);
    }

    @Test
    void markingStatisticNotCurrentCreatesExactObservationSuppression() {
        CountryStatisticAdminDto statistic = statistic();
        UpdateStatisticRequest request = new UpdateStatisticRequest(null, null, null, false, null);
        when(repository.update(statistic.id(), request)).thenReturn(Optional.of(statistic));
        when(storageService.resolveStatisticAssetUrl(statistic.id(), statistic.imageUrl()))
                .thenReturn("/api/assets/statistics/84");

        service.updateStatistic(statistic.id(), request);

        verify(repository).createSuppression(statistic.id());
    }

    @Test
    void markingStatisticCurrentResolvesExactObservationSuppression() {
        CountryStatisticAdminDto statistic = statistic();
        UpdateStatisticRequest request = new UpdateStatisticRequest(null, null, null, true, null);
        when(repository.update(statistic.id(), request)).thenReturn(Optional.of(statistic));
        when(storageService.resolveStatisticAssetUrl(statistic.id(), statistic.imageUrl()))
                .thenReturn("/api/assets/statistics/84");

        service.updateStatistic(statistic.id(), request);

        verify(repository).resolveSuppression(statistic.id());
    }

    private CountryStatisticAdminDto statistic() {
        return new CountryStatisticAdminDto(
                84L,
                12L,
                "SE",
                "Sweden",
                "WIKIMEDIA_CURRENCY",
                "WIKIMEDIA_BANKNOTE_IMAGE_1",
                "Wikimedia banknote image 1",
                "CULTURE",
                null,
                "Banknote",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                BigDecimal.valueOf(0.9),
                BigDecimal.valueOf(0.8),
                "2026-09-22T00:00:00Z",
                "https://cdn.example.com/hint-images/wikimedia-currency/se/note.jpg",
                "https://upload.wikimedia.org/note.jpg",
                "https://commons.wikimedia.org/wiki/File:Note.jpg",
                "Example author",
                "Image by Example author via Wikimedia Commons.",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");
    }
}
