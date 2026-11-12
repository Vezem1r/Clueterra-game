package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.mapillary.dto.TileCoord;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.storage.ImageStorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapillaryPanoramaFetcherTest {

    @Test
    void keepsSearchingWhenPanoramaIsLowResolutionOrOriginalDownloadFails() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.of(storage), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.of("""
                        {"data":[
                          {"id":"1","is_pano":true,"width":1440,"height":720,"quality_score":0.95,
                           "thumb_original_url":"https://example.test/poor.jpg",
                           "geometry":{"coordinates":[14.4,50.1]},"captured_at":1700000000000},
                          {"id":"2","is_pano":true,"width":7680,"height":3840,"quality_score":0.90,
                           "thumb_original_url":"https://example.test/unavailable.jpg",
                           "geometry":{"coordinates":[14.5,50.2]},"captured_at":1700000000000},
                          {"id":"3","is_pano":true,"width":5376,"height":2688,"quality_score":0.82,
                           "thumb_original_url":"https://example.test/full.jpg",
                           "geometry":{"coordinates":[14.6,50.3]},"captured_at":1700000000000}
                        ]}
                        """));
        when(tileScanner.findPanoramaIdsByTile(anyDouble(), anyDouble(), anyInt(), eq("token"), anySet()))
                .thenReturn(Map.of());
        when(storage.storeDirect("https://example.test/unavailable.jpg", "mapillary"))
                .thenReturn(Optional.empty());
        when(storage.storeDirect("https://example.test/full.jpg", "mapillary"))
                .thenReturn(Optional.of("mapillary/full.jpg"));

        var panoramas = fetcher.fetchPanoramas(
                "CZ", List.of(new double[]{50.3, 14.6}), List.of(), List.of(),
                "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas).hasSize(1);
        assertThat(panoramas.get("MAPILLARY_LANDMARK_PANO_1").imageUrl())
                .isEqualTo("mapillary/full.jpg");
        assertThat(panoramas.get("MAPILLARY_LANDMARK_PANO_1").originalImageUrl())
                .isEqualTo("https://example.test/full.jpg");
        verify(storage, never()).storeDirect("https://example.test/poor.jpg", "mapillary");
    }

    @Test
    void tileScanSkipsWeakMetadataAndContinuesToNextImage() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.empty(), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.empty());
        when(tileScanner.findPanoramaIdsByTile(anyDouble(), anyDouble(), anyInt(), eq("token"), anySet()))
                .thenReturn(Map.of(new TileCoord(1, 1), List.of(1L, 2L)));
        when(httpClient.get(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn("""
                        {"id":"1","is_pano":true,"width":1440,"height":720,"quality_score":0.35,
                         "thumb_original_url":"https://example.test/poor.jpg",
                         "geometry":{"coordinates":[14.4,50.1]},"captured_at":1700000000000}
                        """)
                .thenReturn("""
                        {"id":"2","is_pano":true,"width":7680,"height":3840,"quality_score":0.84,
                         "thumb_original_url":"https://example.test/full.jpg",
                         "geometry":{"coordinates":[14.5,50.2]},"captured_at":1700000000000}
                        """);

        var panoramas = fetcher.fetchPanoramas(
                "CZ", List.of(), List.of(new double[]{50.2, 14.5}), List.of(),
                "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas).hasSize(1);
        assertThat(panoramas.get("MAPILLARY_CITY_PANO_1").imageUrl())
                .isEqualTo("https://example.test/full.jpg");
    }

    @Test
    void landmarkSearchUsesActualPointsOfInterestAndRejectsRepeatedSequence() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.empty(), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.of("""
                        {"data":[
                          {"id":"1","is_pano":true,"width":5760,"height":2880,"quality_score":0.90,
                           "thumb_original_url":"https://example.test/times-square.jpg",
                           "geometry":{"coordinates":[-73.9855,40.7580]},"captured_at":1700000000000,
                           "sequence":"same-walk"}
                        ]}
                        """))
                .thenReturn(Optional.of("""
                        {"data":[
                          {"id":"2","is_pano":true,"width":5760,"height":2880,"quality_score":0.95,
                           "thumb_original_url":"https://example.test/repeated.jpg",
                           "geometry":{"coordinates":[-73.9709,40.7758]},"captured_at":1700000000000,
                           "sequence":"same-walk"},
                          {"id":"3","is_pano":true,"width":5760,"height":2880,"quality_score":0.82,
                           "thumb_original_url":"https://example.test/terrace.jpg",
                           "geometry":{"coordinates":[-73.9709,40.7758]},"captured_at":1700000000000,
                           "sequence":"another-walk"}
                        ]}
                        """));

        var panoramas = fetcher.fetchPanoramas(
                "US", List.of(new double[]{40.7580, -73.9855}, new double[]{40.7758, -73.9709}),
                List.of(), List.of(), "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas).hasSize(2);
        assertThat(panoramas.get("MAPILLARY_LANDMARK_PANO_1").originalImageUrl())
                .isEqualTo("https://example.test/times-square.jpg");
        assertThat(panoramas.get("MAPILLARY_LANDMARK_PANO_2").originalImageUrl())
                .isEqualTo("https://example.test/terrace.jpg");
        verify(httpClient).getOptional(eq(DataSource.MAPILLARY),
                contains("lat=40.758&lng=-73.9855&radius=50"),
                eq(Duration.ofSeconds(30)));
    }

    @Test
    void citySearchStoresOnlyOnePanoramaPerSeed() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.empty(), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.of("""
                        {"data":[
                          {"id":"1","is_pano":true,"width":5760,"height":2880,"quality_score":0.90,
                           "thumb_original_url":"https://example.test/first.jpg",
                           "geometry":{"coordinates":[-87.63,41.88]},"captured_at":1700000000000,
                           "sequence":"walk-one"},
                          {"id":"2","is_pano":true,"width":5760,"height":2880,"quality_score":0.85,
                           "thumb_original_url":"https://example.test/second.jpg",
                           "geometry":{"coordinates":[-87.625,41.88]},"captured_at":1700000000000,
                           "sequence":"walk-two"}
                        ]}
                        """));

        var panoramas = fetcher.fetchPanoramas(
                "US", List.of(), List.of(new double[]{41.88, -87.63}), List.of(),
                "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas).containsOnlyKeys("MAPILLARY_CITY_PANO_1");
    }

    @Test
    void landmarkFallsBackToNearbyPanoramaWhenExactSiteHasNone() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.empty(), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.of("{\"data\":[]}"))
                .thenReturn(Optional.of("""
                        {"data":[
                          {"id":"1","is_pano":true,"width":5760,"height":2880,"quality_score":0.99,
                           "thumb_original_url":"https://example.test/too-far.jpg",
                           "geometry":{"coordinates":[2.2945,48.8630]},"captured_at":1700000000000},
                          {"id":"2","is_pano":true,"width":5760,"height":2880,"quality_score":0.80,
                           "thumb_original_url":"https://example.test/nearby.jpg",
                           "geometry":{"coordinates":[2.2945,48.8590]},"captured_at":1700000000000}
                        ]}
                        """));

        var panoramas = fetcher.fetchPanoramas(
                "FR", List.of(new double[]{48.8584, 2.2945}), List.of(), List.of(),
                "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas.get("MAPILLARY_LANDMARK_PANO_1").originalImageUrl())
                .isEqualTo("https://example.test/nearby.jpg");
        verify(httpClient).getOptional(eq(DataSource.MAPILLARY),
                contains("bbox=2.292500,48.856400,2.296500,48.860400"),
                eq(Duration.ofSeconds(30)));
    }

    @Test
    void boundsExpensiveTileFallbacksDuringFullCountryScan() {
        HttpClientHelper httpClient = mock(HttpClientHelper.class);
        MapillaryTileScanner tileScanner = mock(MapillaryTileScanner.class);
        MapillaryPanoramaFetcher fetcher = new MapillaryPanoramaFetcher(
                httpClient, new MapillaryResponseParser(new ObjectMapper()), tileScanner, Optional.empty(), new ObjectMapper());

        when(httpClient.getOptional(eq(DataSource.MAPILLARY), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(Optional.empty());
        when(tileScanner.findPanoramaIdsByTile(anyDouble(), anyDouble(), anyInt(), eq("token"), anySet()))
                .thenReturn(Map.of());
        List<double[]> seeds = List.of(
                new double[]{40, -73}, new double[]{41, -74}, new double[]{42, -75},
                new double[]{43, -76}, new double[]{44, -77});

        var panoramas = fetcher.fetchPanoramas(
                "US", List.of(), seeds, seeds,
                "https://graph.mapillary.com", "token", Duration.ofSeconds(30));

        assertThat(panoramas).isEmpty();
        verify(tileScanner, times(3)).findPanoramaIdsByTile(
                anyDouble(), anyDouble(), eq(2), eq("token"), anySet());
        verify(tileScanner, times(2)).findPanoramaIdsByTile(
                anyDouble(), anyDouble(), eq(3), eq("token"), anySet());
    }
}
